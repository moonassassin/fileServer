package com.akcome.file.srv;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.akcome.file.config.FileConfigInfo;
import com.akcome.file.config.FileConfigService;

@Service
public abstract class AbstractFileService {
	protected final Logger logger = LoggerFactory.getLogger(getClass());
	private static final String DEFAULT_FUNC = "default";
	@Autowired
	private FileConfigService fileMgr;
	@Value("${fs.tmp.dir}")
	protected String fileTmpDir;
	@Value("${fs.dir}")
	protected String fileDir;

	/**
	 * 获取目标应用的所有配置信息集合，key为配置种类，value为配置信息类FileConfigInfo
	 * 
	 * @param appId
	 *            应用标识
	 * @return 目标应用的所有配置信息集合
	 */
	public Map<String, FileConfigInfo> getFileConfigs(String appId) {
		List<FileConfigInfo> configs = fileMgr.getFileConfigs(appId);
		if (configs != null) {
			return configs.stream().collect(Collectors.toMap(FileConfigInfo::getFunc, (v) -> v));
		} else {
			return null;
		}
	}

	/**
	 * 获取目标应用的默认配置
	 * 
	 * @param appId
	 *            应用标识
	 * @return 默认配置信息
	 */
	public FileConfigInfo getFileConfig(String appId) {
		return getFileConfig(appId, null);
	}

	/**
	 * 获取目标应用的目标配置，当配置名(func)为空时，查询默认配置
	 * 
	 * @param appId
	 *            应用标识
	 * @param func
	 *            配置名
	 * @return 目标配置信息
	 */
	public FileConfigInfo getFileConfig(String appId, String func) {
		Map<String, FileConfigInfo> configs = getFileConfigs(appId);
		if (StringUtils.isEmpty(func)) {
			func = DEFAULT_FUNC;
		}
		if (configs == null) {
			logger.warn("no file configs found for app:{}, return", appId);
			return null;
		}
		FileConfigInfo config = configs.get(func);
		if (config == null) {
			logger.warn("invalid func:{} use default", func);
			config = configs.get(DEFAULT_FUNC);
		}
		return config;
	}
}
