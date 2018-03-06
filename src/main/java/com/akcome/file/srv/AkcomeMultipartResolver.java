package com.akcome.file.srv;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import com.akcome.file.config.FileConfigInfo;

@Component
public class AkcomeMultipartResolver extends StandardServletMultipartResolver {
	@Autowired
	private FileService fileSrv;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public AkcomeMultipartResolver() {
		setResolveLazily(true);
	}

	@Override
	public MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) throws MultipartException {
		StandardMultipartHttpServletRequest req = new StandardMultipartHttpServletRequest(request, true) {
			@Override
			protected void initializeMultipart() {
				checkSize(getRequest());
				super.initializeMultipart();
			}
		};
		return req;
	}

	private void checkSize(HttpServletRequest request) throws MultipartException {
		logger.debug("request method:{} and url:{}", request.getMethod(), request.getRequestURL());
		if (request.getRequestURI().startsWith("/file/")) {
			String appId = parseGroupFromUrl(request.getRequestURI());
			if (request.getRequestURI().contains("/tmp/")) {
				appId = FileService.DEFAULT_GROUP;
			}
			String func = request.getParameter("func");
			FileConfigInfo config = fileSrv.getFileConfig(appId, func);
			if (config == null) {
				throw new MultipartException("invalid request");
			}
			if (request.getContentLengthLong() > config.getMaxTotalSize()) {
				logger.warn("upload exceed max size:{} for appId:{} and function:{}", config.getMaxTotalSize(), appId,
						func);
				throw new MaxUploadSizeExceededException(config.getMaxTotalSize());
			}
		}
	}

	private String parseGroupFromUrl(String uri) {
		int idx = uri.indexOf("?");
		if (idx > -1) {
			uri = uri.substring(0, idx);
		}
		uri = uri.replaceAll("/file/", "").replaceAll("/upload", "");
		// 下面的操作是将v1或者v2等后来增加的一级路径去除
		idx = uri.indexOf("/");
		if (idx <= -1) {
			idx = 0;
			logger.warn("upload file path is not like this:/file/***/***/upload");
			return uri;
		}
		uri = uri.substring(idx + 1);
		return uri;
	}
}
