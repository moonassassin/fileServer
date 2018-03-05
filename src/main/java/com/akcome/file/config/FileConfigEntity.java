package com.akcome.file.config;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

@Data
@Entity
@Table(name = "file_configs", schema = "file_server", catalog = "file_server")
public class FileConfigEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column
	private Long id;
	private String appId;
	private String func;
	private String path;
	private Long maxSize;
	private Long maxTotalSize;
	private Integer timeout;
	private Boolean validateUser;
}
