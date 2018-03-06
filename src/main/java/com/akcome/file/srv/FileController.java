package com.akcome.file.srv;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.akcome.common.exception.BusinessException;
import com.akcome.common.exception.SystemException;
import com.akcome.common.file.FileClientUtil;
import com.akcome.common.request.in.FileInfo;
import com.akcome.common.web.AbstractWebController;
import com.akcome.common.web.out.AjaxResult;

@Controller
public class FileController extends AbstractWebController {
	@Autowired
	private FileService fileSvc;

	@RequestMapping(value = "/file/v1/{group}/upload", method = { RequestMethod.POST })
	@ResponseBody
	public AjaxResult<String> uploadFile(HttpServletRequest request, HttpServletResponse response,
			@PathVariable String group, @RequestParam(required = false) String schema) throws BusinessException {
		AjaxResult<String> ret = AjaxResult.create(true);
		Map<String, String> fileInfo = uploadFile0(request, response, group);
		ret.setObj(FileClientUtil.constructFilePathV1(group, fileInfo.get("fileUrl"), fileInfo.get("fileName")));
		return ret;
	}

	/**
	 * 将文件保存到指定目录，并返回新的文件名
	 * 
	 * @param request
	 * @param response
	 * @param group
	 * @return
	 * @throws BusinessException
	 */
	private Map<String, String> uploadFile0(HttpServletRequest request, HttpServletResponse response, String group)
			throws BusinessException {
		Map<String, String> map = new HashMap<>();
		String fileUrl = null;
		try {
			if (request instanceof MultipartHttpServletRequest) {
				MultipartHttpServletRequest mRequest = (MultipartHttpServletRequest) request;
				Map<String, MultipartFile> fileMap = mRequest.getFileMap();
				if (fileMap != null && !fileMap.isEmpty()) {
					for (String fileName : fileMap.keySet()) {
						MultipartFile file = fileMap.get(fileName);
						if (file != null && !file.isEmpty()) {
							map.put("fileName", FilenameUtils.getName(file.getOriginalFilename()));
							fileUrl = fileSvc.storeFile(file, group);
							map.put("fileUrl", fileUrl);
							break;
						}
					}
				}
			}
		} catch (Exception e) {
			this.logger.warn("failed upload file", e);
			throw new BusinessException("failed upload file");
		}

		return map;
	}

	@RequestMapping(value = "/file/v1/{group}/download/{fileName}", method = { RequestMethod.GET })
	public void downloadFileV1(HttpServletRequest request, HttpServletResponse response, @PathVariable String fileName,
			@PathVariable String group) throws BusinessException {
		// 通过注解获取fileName时无法获取后缀
		String servletPath = request.getServletPath();
		FileInfo fileInfo = FileClientUtil.parseFilePathV1(servletPath);
		downloadFileV1Internal(response, group, fileInfo.getFileName(), null);
	}

	@RequestMapping(value = "/file/v1/{group}/download/{fileName}/{refName}", method = { RequestMethod.GET })
	public void downloadFileV1WithRefName(HttpServletRequest request, HttpServletResponse response,
			@PathVariable String fileName, @PathVariable String group, @PathVariable String refName)
			throws BusinessException {
		// 通过注解获取refName时无法获取后缀
		String servletPath = request.getServletPath();
		FileInfo fileInfo = FileClientUtil.parseFilePathV1(servletPath);
		downloadFileV1Internal(response, group, fileName, fileInfo.getOrgFileName());
	}

	private void downloadFileV1Internal(HttpServletResponse response, String group, String fileName, String refName)
			throws BusinessException {
		response.setCharacterEncoding("utf-8");
		String dFileName = (StringUtils.isEmpty(refName) ? fileName : refName);
		try {
			response.setHeader("Content-Disposition", "attachment;fileName=" + URLEncoder.encode(dFileName, "UTF-8"));
		} catch (Exception e) {
			response.setHeader("Content-Disposition",
					"attachment;fileName=file." + FilenameUtils.getExtension(dFileName));
		}

		try {
			File file = fileSvc.getFile(fileName, group);
			if (file.exists()) {
				InputStream inputStream = new FileInputStream(file);
				OutputStream os = response.getOutputStream();
				byte[] b = new byte[1024];
				int length;
				while ((length = inputStream.read(b)) > 0) {
					os.write(b, 0, length);
				}
				inputStream.close();
			} else {
				logger.warn("no file:{} found in group:{}", fileName, group);
				response.setStatus(HttpStatus.NOT_FOUND.value());
			}
		} catch (Exception e) {
			this.logger.warn("failed when download file:{} in group:{}", fileName, group, e);
			response.setStatus(HttpStatus.NOT_FOUND.value());
		}
	}

	@RequestMapping(value = "/file/delete", method = RequestMethod.POST)
	@ResponseBody
	public AjaxResult<?> deleteFile(@RequestBody FileInfo fileInfo) throws SystemException {
		AjaxResult<?> ret = null;
		try {
			fileSvc.deleteFile(fileInfo);
			ret = AjaxResult.create(true);
		} catch (Exception e) {
			logger.warn("failed to delete file:{}", fileInfo, e);
			ret = AjaxResult.create(false, e.getMessage());
		}
		return ret;
	}

	@RequestMapping(value = "/file/switchgroup", method = RequestMethod.POST)
	@ResponseBody
	public AjaxResult<?> switchFileGroup(@RequestBody FileInfo fileInfo, @RequestParam String targetGroup) {
		AjaxResult<?> ret = null;
		try {
			fileSvc.switchFileGroup(fileInfo, targetGroup);
			ret = AjaxResult.create(true);
		} catch (Exception e) {
			logger.warn("failed to switch file:{} to group:{}", fileInfo, targetGroup, e);
			ret = AjaxResult.create(false, e.getMessage());
		}
		return ret;
	}

}
