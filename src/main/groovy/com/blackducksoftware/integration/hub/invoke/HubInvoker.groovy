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
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import pl.jalokim.propertiestojson.util.PropertiesToJsonParser
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;


@Component
class HubInvoker {
	//	@Value('${blackduck.hub.url}')
	String hubUrl = "http://int-hub01.dc1.lan:8080/"

	//	@Value('${blackduck.hub.timeout}')
	//	String hubTimeout

	//	@Value('${blackduck.hub.username}')
	//String hubUsername = this needs to be hardcoded for the moment - will change when connected to hub-detect...

	//	@Value('${blackduck.hub.password}')
	//String hubPassword = this needs to be hardcoded for the moment - will change when connected to hub-detect...

	//	@Value('${blackduck.hub.auto.import.cert}')
	//	String hubAutoImportCert
	//
	//	@Value('${blackduck.hub.proxy.host}')
	//	String hubProxyHost
	//
	//	@Value('${blackduck.hub.proxy.port}')
	//	String hubProxyPort
	//
	//	@Value('${blackduck.hub.proxy.username}')
	//	String hubProxyUsername
	//
	//	@Value('${blackduck.hub.proxy.password}')
	//	String hubProxyPassword

	//	@Value('${invoke.hub.api.endpoint}')
	//	String hubApiEndpoint = "api/projects"

	//	@Value('${invoke.hub.api.verb}')
	//	String hubApiVerb = "POST"

	//	@Value('${invoke.hub.api.payload.path}')
	//	String hubApiPayloadPath = "jsonPayload.json"

	//	@Value('${invoke.hub.api.query.variables}')
	//	String hubApiQueryVariables

	//	@Value('${invoke.hub.api.query.names}')
	//	String hubApiQueryNames
	//
	//	@Value('${invoke.hub.api.query.values}')
	//	String hubApiQueryValues



	String findFile(String dir, String targetName) {
		File file = new File(dir)
		if (file.getName().equals(targetName)) {
			return dir
		}
		if (file.isDirectory()) {
			for (String child : file.listFiles()) {
				String temp = findFile(child, targetName)
				if (temp != null) return temp
			}
		}
		return null
	}


	void connectToHub(Logger log) {
		Slf4jIntLogger logger = new Slf4jIntLogger(log)

		HubServerConfig hubServerConfig = createHubServerConfig(logger);
		RestConnection restConnection = hubServerConfig.createCredentialsRestConnection(logger)
		restConnection.connect()
		HubRequest hubRequest = new HubRequest(restConnection)


		String propertiesFile = findFile("src", "application.properties")
		if (propertiesFile != null) {
			String json = PropertiesToJsonParser.parseToJson(new FileInputStream(new File(propertiesFile)))
			JsonElement masterTree = new Gson().toJsonTree(new JsonParser().parse(json))
			GsonBuilder gsonBuilder = new GsonBuilder()

			JsonElement metaInfoTree = masterTree.getAsJsonObject().get("information")
			int numSteps = metaInfoTree.getAsJsonObject().get("chainSteps").getAsInt()
			//String hubUrl = metaInfoTree.getAsJsonObject().get("hubUrl")

			for (int i = 1; i <= numSteps; i++) {
				JsonElement chainStepTree = masterTree.getAsJsonObject().get(i + "")

				String hubApiVerb = chainStepTree.getAsJsonObject().get("verb")
				hubApiVerb = hubApiVerb.substring(1, hubApiVerb.length() - 1) //eliminating quotation marks
				String hubApiEndpoint = chainStepTree.getAsJsonObject().get("endpoint")
				hubApiEndpoint = hubApiEndpoint.substring(1, hubApiEndpoint.length() - 1)

				JsonElement jsonSubTree = chainStepTree.getAsJsonObject().get("content")
				String formattedJsonSubTree = gsonBuilder.setPrettyPrinting().create().toJson(jsonSubTree)

				String fileName = "chainStep_" + i + ".json"
				FileWriter fileWriter = new FileWriter(fileName)
				fileWriter.write(formattedJsonSubTree)
				fileWriter.close()

				hubRequest.url = hubUrl + hubApiEndpoint
				Response response
				if (hubApiVerb.equalsIgnoreCase("get")) {
					response = hubRequest.executeGet()
					println(new GsonBuilder().setPrettyPrinting().create().toJson(new JsonParser().parse(response.body().string())))
				} else if (hubApiVerb.equalsIgnoreCase("post")) {
					response = hubRequest.executePost(new Scanner(new File(fileName)).useDelimiter("\\Z").next())
					println(response)
				} else if (hubApiVerb.equalsIgnoreCase("delete")) {
					response = hubRequest.executeDelete()
					println(response.body().string())
				}
				response.close()
			}
		} else {
			println "Error: no properties file found"
		}
	}

	HubServerConfig createHubServerConfig(Slf4jIntLogger slf4jIntLogger) {
		HubServerConfigBuilder hubServerConfigBuilder = new HubServerConfigBuilder()

		hubServerConfigBuilder.setHubUrl(getHubUrl())
		//hubServerConfigBuilder.setTimeout(getHubTimeout())
		hubServerConfigBuilder.setUsername(getHubUsername())
		hubServerConfigBuilder.setPassword(getHubPassword())

		//		hubServerConfigBuilder.setProxyHost(getHubProxyHost())
		//		hubServerConfigBuilder.setProxyPort(getHubProxyPort())
		//		hubServerConfigBuilder.setProxyUsername(getHubProxyUsername())
		//		hubServerConfigBuilder.setProxyPassword(getHubProxyPassword())

		//hubServerConfigBuilder.setAutoImportHttpsCertificates(getHubAutoImportCert())
		hubServerConfigBuilder.setLogger(slf4jIntLogger)

		hubServerConfigBuilder.build()
	}
}
