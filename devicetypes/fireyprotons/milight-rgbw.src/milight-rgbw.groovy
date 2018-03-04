/**
 *  Copyright 2018 Erik von Asten (fireyProtons)
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
 *  Milight RGBW Light
 *
 *  Author: Erik von Asten
 *  Date: 2015-7-12
 */

preferences {
		input("ip", "text", title: "IP", description: "The ip of your milight-hub i.e. 192.168.1.110", default: "192.168.10.191")
        input("port", "text", title: "Port", description: "The port your HTTP service is running on. The default is 80", default: "80")
        input("deviceID", "text", title: "Device ID", description: "The device ID associated with the bulb i.e. 0xABCD")
		input("group", "enum", title: "Group Number", description: "The group number associated with the bulb or group", options: ["1", "2", "3", "4", "All"])
        input("deviceType", "enum", title: "Device Type", description: "The type of Bulb", options: ["RGBW", "CCT", "RGB+CCT", "RGB", "FUT089"])
}

metadata {
	definition (name: "Milight RGBW", namespace: "FireyProtons", author: "Erik von Asten") {
		capability "Switch Level"
		capability "Color Control"
		capability "Color Temperature"
		capability "Switch"
		capability "Refresh"
		capability "Actuator"
		capability "Sensor"

		command "reset"
        
	}

	simulator {
	}

	standardTile("switch", "device.switch", width: 1, height: 1, canChangeIcon: true) {
		state "on", label:'${name}', action:"switch.off", icon:"st.lights.philips.hue-single", backgroundColor:"#00a0dc", nextState:"off"
		state "off", label:'${name}', action:"switch.on", icon:"st.lights.philips.hue-single", backgroundColor:"#ffffff", nextState:"on"
	}
	standardTile("reset", "device.reset", inactiveLabel: false, decoration: "flat") {
		state "default", label:"Reset Color", action:"reset", icon:"st.lights.philips.hue-single"
	}
	standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
		state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
	}
	controlTile("levelSliderControl", "device.level", "slider", height: 1, width: 2, inactiveLabel: false, range:"(0..100)") {
		state "level", action:"switch level.setLevel"
	}
	controlTile("rgbSelector", "device.color", "color", height: 3, width: 3, inactiveLabel: false) {
		state "color", action:"setColor"
	}
	valueTile("level", "device.level", inactiveLabel: false, decoration: "flat") {
		state "level", label: 'Level ${currentValue}%'
	}
    
	main(["switch"])
	details(["switch", "levelSliderControl", "rgbSelector", "reset", "refresh"])
}

def updated() {
	response(refresh())
}

def parse(description) {
	log.debug description
}

def on() {
	def cmds = []
    cmds << putAction("/gateways/${deviceID}/${deviceType}/${group}", '{"status":"on"}')
    return cmds
}

def off() {
	def cmds = []
    cmds << putAction("/gateways/${deviceID}/${deviceType}/${group}", '{"status":"off"}')
    return cmds
}

def setLevel(level) {
    def info = '{"level"'+":${level}}"
    def cmds = []
    cmds << putAction("/gateways/${deviceID}/${deviceType}/${group}", info)
    return cmds
}

def refresh() {
	def cmds = []
    cmds << getAction("/gateways/${deviceID}/${deviceType}/${group}")
    return cmds
}

def setSaturation(percent) {
	log.debug "setSaturation($percent)"
	setColor(saturation: percent)
}

def setColor(value) {
	def result = '{"color":{"r"'+":${value.red},"+'"g"'+":${value.green},"+'"b"'+":${value.blue}}}"
	log.debug "setColor: ${result}"
    def cmds = []
    cmds << putAction("/gateways/${deviceID}/${deviceType}/${group}", result)
    return cmds
}

def setColorTemperature(percent) {
	if(percent > 99) percent = 99
	int warmValue = percent * 255 / 99
	command(zwave.switchColorV3.switchColorSet(red:0, green:0, blue:0, warmWhite:warmValue, coldWhite:(255 - warmValue)))
}

def reset() {
	def cmds = []
    cmds << putAction("/gateways/${deviceID}/${deviceType}/${group}", '{"command":"set_white"}')
    return cmds
}

def rgbToHSV(red, green, blue) {
	float r = red / 255f
	float g = green / 255f
	float b = blue / 255f
	float max = [r, g, b].max()
	float delta = max - [r, g, b].min()
	def hue = 13
	def saturation = 0
	if (max && delta) {
		saturation = 100 * delta / max
		if (r == max) {
			hue = ((g - b) / delta) * 100 / 6
		} else if (g == max) {
			hue = (2 + (b - r) / delta) * 100 / 6
		} else {
			hue = (4 + (r - g) / delta) * 100 / 6
		}
	}
	[hue: hue, saturation: saturation, value: max * 100]
}

def huesatToRGB(float hue, float sat) {
	while(hue >= 100) hue -= 100
	int h = (int)(hue / 100 * 6)
	float f = hue / 100 * 6 - h
	int p = Math.round(255 * (1 - (sat / 100)))
	int q = Math.round(255 * (1 - (sat / 100) * f))
	int t = Math.round(255 * (1 - (sat / 100) * (1 - f)))
	switch (h) {
		case 0: return [255, t, p]
		case 1: return [q, 255, p]
		case 2: return [p, 255, t]
		case 3: return [p, q, 255]
		case 4: return [t, p, 255]
		case 5: return [255, p, q]
	}
}

private storeNetworkDeviceId(){
    def iphex = convertIPtoHex(settings.ip).toUpperCase()
    def porthex = convertPortToHex(settings.port)
    device.deviceNetworkId = "$iphex:$porthex" 
    
//    log.debug device.deviceNetworkId
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

private getHeader(){
    def headers = [:]
    headers.put("Host", getHostAddress())
    headers.put("Content-Type", "application/json")
    return headers
}

private putAction(uri, data){   
  def headers = getHeader()
//  log.debug "put data is: " + data
  def hubAction = new physicalgraph.device.HubAction(
    method: "PUT",
    path: uri,
    headers: headers,
    body: data
  )
  return hubAction    
}

private getAction(uri){ 
  //log.debug uri
//  if(password != null && password != "") 
//    userpass = encodeCredentials("admin", password)
    
  def headers = getHeader()

  def hubAction = new physicalgraph.device.HubAction(
    method: "GET",
    path: uri,
    headers: headers
  )
  return hubAction    
}

private getHostAddress() {
	return "${ip}:${port}"
}
