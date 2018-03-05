package com.akcome.file.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

@Transactional
@Service
public class FileConfigService {
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private FileConfigDao dao;
	private Map<String, List<FileConfigInfo>> configMap;

	@PostConstruct
	public void init() {
		transactionTemplate.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				loadFromDB();
			}
		});
	}

	private void loadFromDB() {
		Map<String, List<FileConfigInfo>> map = new HashMap<>();
		List<FileConfigEntity> list = dao.getAll();
		if (list != null) {
			list.stream().collect(Collectors.groupingBy(FileConfigEntity::getAppId)).forEach((k, v) -> {
				map.put(k,
						v.stream()
								.map(r -> new FileConfigInfo(r.getFunc(), r.getPath(), r.getMaxSize(),
										r.getMaxTotalSize(), r.getTimeout(), r.getValidateUser()))
								.collect(Collectors.toList()));
			});
		}
		configMap = map;
	}

	@Cacheable(value = "fileConfigCache", key = "'fileconfig_'+#appId")
	public List<FileConfigInfo> getFileConfigs(String appId) {
		if (configMap == null) {
			return null;
		} else {
			return configMap.get(appId);
		}
	}
}
