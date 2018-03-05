package com.akcome.file.config;

import lombok.Data;

@Data
public class FileConfigInfo {
	private String func;
	private String path;
	private Long maxSize;
	private Long maxTotalSize;
	private Integer timeout;
	private Boolean validateUser;

	public FileConfigInfo() {
	}

	public FileConfigInfo(String func, String path, Long maxSize, Long maxTotalSize, Integer timeout,
			Boolean validateUser) {
		this.func = func;
		this.path = path;
		this.maxSize = maxSize;
		this.maxTotalSize = maxTotalSize;
		this.timeout = timeout;
		this.validateUser = validateUser;
	}
}
