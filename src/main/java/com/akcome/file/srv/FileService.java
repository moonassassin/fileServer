package com.akcome.file.srv;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.akcome.common.exception.SystemException;
import com.akcome.common.request.in.FileInfo;
import com.akcome.file.config.FileConfigInfo;

import lombok.Data;

@Service
public class FileService extends AbstractFileService {
	public static final String DEFAULT_GROUP = "tmp";
	private Map<String, BlockingQueue<TmpFileInfo>> fileQueueMap = null;

	@PostConstruct
	public void init() {
		fileQueueMap = new HashMap<>();
	}

	/**
	 * 将文件保存到零时文件夹的默认中，返回新的文件名
	 * 
	 * @param file
	 *            要保存的文件
	 * @return 当前文件保存在硬盘中的文件名
	 * @throws SystemException
	 */
	public String storeFile(MultipartFile file) throws SystemException {
		return storeFile(file, DEFAULT_GROUP);
	}

	private File getGroupDir(String group, FileConfigInfo config) {
		return new File(DEFAULT_GROUP.equalsIgnoreCase(group) ? fileTmpDir : fileDir, config.getPath());
	}

	/**
	 * 将文件保存到零时文件夹的分组（group）中，返回新的文件名
	 * 
	 * @param file
	 *            要保存的文件
	 * @param group
	 *            文件分组
	 * @return 当前文件保存在硬盘中的文件名
	 * @throws SystemException
	 */
	public String storeFile(MultipartFile file, String group) throws SystemException {
		try {
			// 获取分组的配置信息
			FileConfigInfo config = getFileConfig(group);
			if (config == null) {
				throw new SystemException("invalid group:" + group);
			}
			// 获取零时文件夹中该分组的文件夹
			File dir = getGroupDir(group, config);
			if (dir.exists() == false) {
				if (dir.mkdirs() == false) {
					throw new SystemException("failed to create folder:" + dir.getAbsolutePath());
				}
			}

			// 将文件写入本地文件中
			File rFile = null;
			String fileName = null;
			do {
				fileName = UUID.randomUUID().toString();
				rFile = new File(dir, fileName + "." + FilenameUtils.getExtension(file.getOriginalFilename()));
			} while (rFile.exists());
			FileUtils.copyInputStreamToFile(file.getInputStream(), rFile);

			// 将文件信息保存到队列中
			if (DEFAULT_GROUP.equalsIgnoreCase(group)) {
				putFileToQueue(new TmpFileInfo(fileName, group, System.currentTimeMillis()));
			}
			return rFile.getName();
		} catch (IOException | InterruptedException e) {
			logger.warn("failed to upload file", e);
			throw new SystemException("failed to upload file", e);
		}
	}

	/**
	 * 将零时文件的文件信息保存到对应分组的队列中
	 * 
	 * @param fileInfo
	 *            零时文件的文件信息
	 * @throws InterruptedException
	 */
	private void putFileToQueue(TmpFileInfo fileInfo) throws InterruptedException {
		// 从map集合中获取文件分组对应的队列
		BlockingQueue<TmpFileInfo> queue = fileQueueMap.get(fileInfo.getGroup());
		// 当没有该分组时，锁定当前类，向集合中添加键值对，key为队列标识（fileInfo.getGroup()），value为新的空队列
		if (queue == null) {
			synchronized (this) {
				queue = fileQueueMap.get(fileInfo.getGroup());
				if (queue == null) {
					queue = new LinkedBlockingQueue<>();
					fileQueueMap.put(fileInfo.getGroup(), queue);
				}
			}
		}
		// 向队列中存入信息
		queue.put(fileInfo);
	}

	public File getFile(String fileName, String group) throws Exception {
		FileConfigInfo config = getFileConfig(group);
		if (config == null) {
			throw new SystemException("invalid group:" + group);
		}
		File dir = getGroupDir(group, config);
		if (dir.exists()) {
			File rFile = new File(dir, fileName);
			if (rFile.exists()) {
				return rFile;
			}
		}
		return null;
	}

	@Scheduled(cron = "0 0/30 * * * ?")
	public void clearTimeoutFile() {
		logger.info("start the clear timeout file job");
		try {
			if (fileQueueMap != null) {
				for (String group : fileQueueMap.keySet()) {
					BlockingQueue<TmpFileInfo> fileQueue = fileQueueMap.get(group);
					FileConfigInfo config = getFileConfig(group);
					if (config == null) {
						logger.warn("found invalid group:{}, skip ", group);
						continue;
					}
					do {
						TmpFileInfo file = fileQueue.poll();
						if (file == null) {
							logger.info("no file in queue, break job");
							break;
						}
						if (file.getTimeStamp() + config.getTimeout() * 60 * 1000 > System.currentTimeMillis()) {
							logger.info("file not old enough, break job");
							break;
						}
						removeTmpFile(file);
					} while (true);
				}
			}
		} catch (Exception e) {
			logger.warn("failed to loop timeout file queue", e);
		}
		logger.info("finished the clear timeout file job");
	}

	private void removeTmpFile(TmpFileInfo fileInfo) {
		try {
			FileConfigInfo config = getFileConfig(fileInfo.getGroup());
			File dir = new File(fileTmpDir, config.getPath());
			if (dir.exists()) {
				File rFile = new File(dir, fileInfo.getFileName());
				if (rFile.exists()) {
					rFile.delete();
				} else {
					logger.info("tmp file:{} not exist", fileInfo.getFileName());
				}
			} else {
				logger.info("tmp dir not exist");
			}
		} catch (Exception e) {
			logger.warn("failed to remove tmp file:{}", fileInfo.getFileName(), e);
		}
	}

	public void deleteFile(FileInfo fileInfo) throws SystemException {
		if (fileInfo == null || StringUtils.isEmpty(fileInfo.getOrgFileName())) {
			logger.warn("empty fileInfo:{},return", fileInfo);
			return;
		}
		try {
			// 获取分组文件夹
			String group = (StringUtils.isEmpty(fileInfo.getGroup()) ? DEFAULT_GROUP : fileInfo.getGroup());
			FileConfigInfo config = getFileConfig(group);
			if (config == null) {
				throw new SystemException("invalid group:" + group);
			}
			File dir = getGroupDir(group, config);
			if (dir.exists()) {
				// 当分组文件夹存在时，获取目标文件对象
				File rFile = new File(dir, fileInfo.getOrgFileName());
				if (rFile.exists()) {
					// 当目标文件存在时，删除文件
					rFile.delete();
				}
			}
		} catch (Exception e) {
			logger.warn("failed to delete file:{}", fileInfo, e);
			throw new SystemException("file delete failed", e);
		}
	}

	public void switchFileGroup(FileInfo tmpFileInfo, String targetGroup) throws SystemException {
		if (tmpFileInfo == null || StringUtils.isEmpty(tmpFileInfo.getOrgFileName()) || StringUtils.isEmpty(targetGroup)) {
			logger.warn("empty fileInfo:{} or targetGroup:{},return", tmpFileInfo, targetGroup);
			return;
		}
		try {
			// 获取分组文件夹
			String group = (StringUtils.isEmpty(tmpFileInfo.getGroup()) ? DEFAULT_GROUP : tmpFileInfo.getGroup());
			if (!targetGroup.equalsIgnoreCase(group)) {
				FileConfigInfo config = getFileConfig(group);
				if (config == null) {
					throw new SystemException("invalid group:" + group);
				}
				FileConfigInfo targetConfig = getFileConfig(targetGroup);
				if (targetConfig == null) {
					throw new SystemException("invalid group:" + targetGroup);
				}
				File srcDir = getGroupDir(group, config);
				if (srcDir.exists()) {
					File srcFile = new File(srcDir, tmpFileInfo.getOrgFileName());
					if (srcFile.exists()) {
						File targetDir = getGroupDir(targetGroup, targetConfig);
						FileUtils.moveFileToDirectory(srcFile, targetDir, true);
					} else {
						logger.warn("source file:{} not exist", tmpFileInfo);
					}
				}
			}
		} catch (Exception e) {
			logger.warn("failed to move file", e);
			throw new SystemException("file move failed", e);
		}
	}

	@Data
	private static class TmpFileInfo {
		private String fileName;
		private String group;
		private long timeStamp;

		public TmpFileInfo(String fileName, String group, long timeStamp) {
			this.fileName = fileName;
			this.group = group;
			this.timeStamp = timeStamp;
		}
	}
}
