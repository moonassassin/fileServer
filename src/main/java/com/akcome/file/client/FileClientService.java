package com.akcome.file.client;

import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.akcome.common.request.in.FileInfo;
import com.akcome.common.web.out.AjaxResult;

@FeignClient(value = "fileWebServer", name = "fileWebServer")
public interface FileClientService {

	@RequestMapping(value = "/file/delete", method = RequestMethod.POST)
	public AjaxResult<?> deleteFile(FileInfo fileInfo);

	@RequestMapping(value = "/file/switchgroup", method = RequestMethod.POST)
	public AjaxResult<?> switchFileGroup(FileInfo fileInfo, @RequestParam(value = "targetGroup") String targetGroup);

}
