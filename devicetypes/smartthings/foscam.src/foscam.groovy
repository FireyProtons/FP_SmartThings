/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Foscam
 *
 *  Author: SmartThings
 *  Date: 2014-02-04
 */
 metadata {
	definition (name: "Foscam", namespace: "FireyProtons", author: "FireyProtons") {
		capability "Actuator"
		capability "Sensor"
		capability "Image Capture"
        capability "Motion Sensor"
        capability "Video Camera"
        capability "Configuration"
        capability "Video Capture"
        capability "Refresh"
        capability "Switch"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	//TODO:encrypt these settings and make them required:true
	preferences {
		input "ip", "text", title: "IP Address", description: "Your Foscam IP address, eg 192.168.1.110", required: false
        input "port", "text", title: "Port", description: "Your Foscam Port", required: false
        input "username", "text", title: "Username", description: "Your Foscam Username", required: false
		input "password", "password", title: "Password", description: "Your Foscam Password", required: false
	}

	tiles(scale: 2) {
		multiAttributeTile(name: "videoPlayer", type: "videoPlayer", width: 6, height: 4) {
			tileAttribute("device.switch", key: "CAMERA_STATUS") {
				attributeState("on", label: "Active", icon: "st.camera.dlink-indoor", action: "switch.off", backgroundColor: "#79b821", defaultState: true)
				attributeState("off", label: "Inactive", icon: "st.camera.dlink-indoor", action: "switch.on", backgroundColor: "#ffffff")
				attributeState("restarting", label: "Connecting", icon: "st.camera.dlink-indoor", backgroundColor: "#53a7c0")
				attributeState("unavailable", label: "Unavailable", icon: "st.camera.dlink-indoor", action: "refresh.refresh", backgroundColor: "#F22000")
			}

			tileAttribute("device.errorMessage", key: "CAMERA_ERROR_MESSAGE") {
				attributeState("errorMessage", label: "", value: "", defaultState: true)
			}

			tileAttribute("device.camera", key: "PRIMARY_CONTROL") {
				attributeState("on", label: "Active", icon: "st.camera.dlink-indoor", backgroundColor: "#79b821", defaultState: true)
				attributeState("off", label: "Inactive", icon: "st.camera.dlink-indoor", backgroundColor: "#ffffff")
				attributeState("restarting", label: "Connecting", icon: "st.camera.dlink-indoor", backgroundColor: "#53a7c0")
				attributeState("unavailable", label: "Unavailable", icon: "st.camera.dlink-indoor", backgroundColor: "#F22000")
			}

			tileAttribute("device.startLive", key: "START_LIVE") {
				attributeState("live", action: "start", defaultState: true)
			}

			tileAttribute("device.stream", key: "STREAM_URL") {
				attributeState("activeURL", defaultState: true)
			}
            /*
			tileAttribute("device.profile", key: "STREAM_QUALITY") {
				attributeState("1", label: "720p", action: "setProfileHD", defaultState: true)
				attributeState("2", label: "h360p", action: "setProfileSDH", defaultState: true)
				attributeState("3", label: "l360p", action: "setProfileSDL", defaultState: true)
			}	*/		
		}
        
        standardTile("image", "device.image", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: true) {
			state "default", label: "", action: "", icon: "st.camera.dropcam-centered", backgroundColor: "#FFFFFF"
		}

		carouselTile("cameraDetails", "device.image", width: 3, height: 2) { }

		standardTile("take", "device.image", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
			state "take", label: "Take", action: "Image Capture.take", icon: "st.camera.dropcam", backgroundColor: "#FFFFFF", nextState:"taking"
			state "taking", label:'Taking', action: "", icon: "st.camera.dropcam", backgroundColor: "#53a7c0"
			state "image", label: "Take", action: "Image Capture.take", icon: "st.camera.dropcam", backgroundColor: "#FFFFFF", nextState:"taking"
		}

		/*standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"getDeviceInfo", icon:"st.secondary.refresh"
		}*/

		main "videoPlayer"
		details(["videoPlayer", "cameraDetails", "take"])
	}
}

mappings {
   path("/getInHomeURL") {
       action:
       [GET: "getInHomeURL"]
   }
}

def installed() {
	configure()
}

def updated() {
	configure()
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"

	def map = stringToMap(description)
	log.debug map

	def result = []

//	if (map.bucket && map.key)
	if (map.requestId && map.tempImageKey)
	{ //got a s3 pointer
		//putImageInS3(map)
        try {
 			storeTemporaryImage(map.tempImageKey, getPictureName())
 		} catch(Exception e) {
 			log.error e
 		}
	}
	else if (map.headers && map.body)
	{ //got device info response

		/*
		TODO:need to figure out a way to reliable know which end the snapshot should be taken at.
		Current theory is that 8xxx series cameras are at /snapshot.cgi and 9xxx series are at /cgi-bin/CGIProxy.fcgi
		*/

		def headerString = new String(map.headers.decodeBase64())
		if (headerString.contains("404 Not Found")) {
			state.snapshot = "/snapshot.cgi"
		}

		if (map.body) {
			def bodyString = new String(map.body.decodeBase64())
			def body = new XmlSlurper().parseText(bodyString)
			def productName = body?.productName?.text()
			if (productName)
			{
				log.trace "Got Foscam Product Name: $productName"
				state.snapshot = "/cgi-bin/CGIProxy.fcgi"
			}
		}
	}

	result
}

// handle commands
def configure() {
	log.debug "Executing 'configure'"
    //sendEvent(name:"switch", value: "on")
    sendEvent(name:"startLive", value: "live")
}

def start() {
	log.trace "start()"
    log.debug parent.state.CameraStreamPath
	def dataLiveVideo = [
		OutHomeURL  : "http://192.168.10.124:89/videostream.cgi?user=guest&pwd=guest",
		InHomeURL   : "http://192.168.10.124:89/videostream.cgi?user=guest&pwd=guest",
		ThumbnailURL: "http://cdn.device-icons.smartthings.com/camera/dlink-indoor@2x.png",
		cookie      : [key: "key", value: "value"]
	]

	def event = [
		name           : "stream",
		value          : groovy.json.JsonOutput.toJson(dataLiveVideo).toString(),
		data		   : groovy.json.JsonOutput.toJson(dataLiveVideo),
		descriptionText: "Starting the livestream",
		eventType      : "VIDEO",
		displayed      : false,
		isStateChange  : true
	]
	sendEvent(event)
}

def getInHomeURL() {
	[InHomeURL: "http://192.168.10.124:89/videostream.cgi?user=guest&pwd=guest"] //parent.state.CameraStreamPath]
}

def take() {
	log.debug "Executing 'take'"
	//Snapshot uri depends on model number:
	//because 8 series uses user and 9 series uses usr -
	//try based on port since issuing a GET with usr to 8 series causes it throw 401 until you reauthorize using basic digest authentication

	def host = getHostAddress()
	def port = host.split(":")[1]
	def path = "/snapshot.cgi?user=${getUsername()}&pwd=${getPassword()}"


	def hubAction = new physicalgraph.device.HubAction(
		method: "GET",
		path: path,
		headers: [HOST:host]
	)
	hubAction.options = [outputMsgToS3:true]
	hubAction
}

/*def getDeviceInfo() {
	log.debug "Executing 'getDeviceInfo'"
	def path = "/cgi-bin/CGIProxy.fcgi"
	def hubAction = new physicalgraph.device.HubAction(
		method: "GET",
		path: path,
		headers: [HOST:getHostAddress()],
		query:[cmd:"getDevInfo", usr:getUsername(), pwd:getPassword()]
	)
}*/

//helper methods
private getPictureName() {
	def pictureUuid = java.util.UUID.randomUUID().toString().replaceAll('-', '')
	return "image" + "_$pictureUuid" + ".jpg"
}

private getUsername() {
	settings.username
}

private getPassword() {
	settings.password
}

private getHostAddress() {
	def iphex = convertIPtoHex(settings.ip).toUpperCase()
    def porthex = convertPortToHex(settings.port)
    device.deviceNetworkId = "$iphex:$porthex" 
}

private hashMD5(String somethingToHash) {
	java.security.MessageDigest.getInstance("MD5").digest(somethingToHash.getBytes("UTF-8")).encodeHex().toString()
}

private calcDigestAuth(String method, String uri) {
	def HA1 = hashMD5("${getUsername}::${getPassword}")
	def HA2 = hashMD5("${method}:${uri}")
	def response = hashMD5("${HA1}::::auth:${HA2}")

	'Digest username="'+ getUsername() + '", realm="", nonce="", uri="'+ uri +'", qop=auth, nc=, cnonce="", response="' + response + '", opaque=""'
}


private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    //log.debug "IP address entered is $ipAddress and the converted hex code is $hex"
    return hex

}

private String convertPortToHex(port) {
    String hexport = port.toString().format( '%04x', port.toInteger() )
    //log.debug "Hexport is " + hexport
    return hexport
}