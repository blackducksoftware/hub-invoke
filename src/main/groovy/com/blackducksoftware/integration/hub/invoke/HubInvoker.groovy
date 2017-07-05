/*
 * Copyright (C) 2017 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.blackducksoftware.integration.hub.invoke

import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import com.blackducksoftware.integration.hub.api.project.ProjectRequestService
import com.blackducksoftware.integration.hub.builder.HubServerConfigBuilder
import com.blackducksoftware.integration.hub.global.HubServerConfig
import com.blackducksoftware.integration.hub.model.view.ProjectView
import com.blackducksoftware.integration.hub.request.HubRequest
import com.blackducksoftware.integration.hub.rest.CredentialsRestConnection
import com.blackducksoftware.integration.hub.rest.RestConnection
import com.blackducksoftware.integration.hub.service.HubServicesFactory
import com.blackducksoftware.integration.log.IntLogger
import com.blackducksoftware.integration.log.PrintStreamIntLogger
import com.blackducksoftware.integration.log.Slf4jIntLogger
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response

@Component
class HubInvoker {
	@Value('${blackduck.hub.url}')
	String hubUrl

	@Value('${blackduck.hub.timeout}')
	String hubTimeout

	@Value('${blackduck.hub.username}')
	String hubUsername

	@Value('${blackduck.hub.password}')
	String hubPassword

	@Value('${blackduck.hub.auto.import.cert}')
	String hubAutoImportCert

	@Value('${blackduck.hub.proxy.host}')
	String hubProxyHost

	@Value('${blackduck.hub.proxy.port}')
	String hubProxyPort

	@Value('${blackduck.hub.proxy.username}')
	String hubProxyUsername

	@Value('${blackduck.hub.proxy.password}')
	String hubProxyPassword

	@Value('${invoke.hub.api}')
	String hubApiEndpoint

	@Value('${invoke.hub.api.verb}')
	String hubApiVerb

	@Value('${invoke.hub.api.payload.path}')
	String hubApiPayloadPath

	@Value('${invoke.hub.api.query.variables}')
	String hubApiQueryVariables
	
	@Value('${invoke.hub.api.query.names}')
	String hubApiQueryNames
	
	@Value('${invoke.hub.api.query.values}')
	String hubApiQueryValues
	
	void connectToHub(Logger log) {
		Slf4jIntLogger logger = new Slf4jIntLogger(log)

		HubServerConfig hubServerConfig = createHubServerConfig(logger);
		RestConnection restConnection = hubServerConfig.createCredentialsRestConnection(logger)
		restConnection.connect();
		HubRequest hubRequest = new HubRequest(restConnection)
		
		hubRequest.url = hubUrl
		hubRequest.addUrlSegments(Arrays.asList(hubApiEndpoint.split("/")))
		
		if (!hubApiQueryNames.isEmpty() && !hubApiQueryValues.isEmpty()) {
			String[] names = hubApiQueryNames.split("&")
			String[] vals = hubApiQueryValues.split("&")
			if (names.length == vals.length) {
				for (int i = 0; i < names.length; i++) {
					hubRequest.addQueryParameter(names[i], vals[i])
				}
			}
		}
			
		Response response
		if (hubApiVerb.equalsIgnoreCase("get")) {
			response = hubRequest.executeGet()
			println (new GsonBuilder().setPrettyPrinting().create().toJson(new JsonParser().parse(response.body().string())))
		} else if (hubApiVerb.equalsIgnoreCase("post")) {
			response = hubRequest.executePost(new Scanner(new File(hubApiPayloadPath)).useDelimiter("\\Z").next())
			println(response.body().string())
		} else if (hubApiVerb.equalsIgnoreCase("delete")) {
			response = hubRequest.executeDelete()
			println(response.body().string())
		}
		response.close()
	}



	HubServerConfig createHubServerConfig(Slf4jIntLogger slf4jIntLogger) {
		HubServerConfigBuilder hubServerConfigBuilder = new HubServerConfigBuilder()

		hubServerConfigBuilder.setHubUrl(getHubUrl())
		//hubServerConfigBuilder.setTimeout(getHubTimeout())
		hubServerConfigBuilder.setUsername(getHubUsername())
		hubServerConfigBuilder.setPassword(getHubPassword())

		hubServerConfigBuilder.setProxyHost(getHubProxyHost())
		hubServerConfigBuilder.setProxyPort(getHubProxyPort())
		hubServerConfigBuilder.setProxyUsername(getHubProxyUsername())
		hubServerConfigBuilder.setProxyPassword(getHubProxyPassword())

		//hubServerConfigBuilder.setAutoImportHttpsCertificates(getHubAutoImportCert())
		hubServerConfigBuilder.setLogger(slf4jIntLogger)

		hubServerConfigBuilder.build()
	}
}
