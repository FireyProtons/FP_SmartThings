/**
 *  Copyright 2018 Erik von Asten
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
 *  Milight (Connect)
 *
 *  Author: Erik von Asten (FireyProtons)
 *  Date: 2018-02-25
 */

definition(
    name: "Milight (Connect)",
    namespace: "FireyProtons",
    author: "Erik von Asten (FireyProtons)",
    description: "Service Manager for Milight bulbs",
    category: "Convenience"
)

preferences {
	page(name: "mainPage")
    page(name: "configurePDevice")
    page(name: "deletePDevice")
    page(name: "changeName")
    page(name: "addDevices", title: "Add Milight Bulbs", content: "addDevices")
    page(name: "manuallyAdd")
    page(name: "manuallyAddConfirm")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: "Manage your Milight bulbs", nextPage: null, uninstall: true, install: true) {
        section("Configure"){
           href "manuallyAdd", title:"Manually Add Device", description:""
        }
        section("Installed Devices"){
            getChildDevices().sort({ a, b -> a["deviceNetworkId"] <=> b["deviceNetworkId"] }).each {
                href "configurePDevice", title:"$it.label", description:"", params: [did: it.deviceNetworkId]
            }
        }
    }
}

def configurePDevice(params){
   if (params?.did || params?.params?.did) {
      if (params.did) {
         state.currentDeviceId = params.did
         state.currentDisplayName = getChildDevice(params.did)?.displayName
      } else {
         state.currentDeviceId = params.params.did
         state.currentDisplayName = getChildDevice(params.params.did)?.displayName
      }
   }  
   if (getChildDevice(state.currentDeviceId) != null) getChildDevice(state.currentDeviceId).configure()
   dynamicPage(name: "configurePDevice", title: "Configure Milight bulbs created with this app", nextPage: null) {
		section {
            app.updateSetting("${state.currentDeviceId}_label", getChildDevice(state.currentDeviceId).label)
            input "${state.currentDeviceId}_label", "text", title:"Device Name", description: "", required: false
            href "changeName", title:"Change Device Name", description: "Edit the name above and click here to change it"
        }
        section {
              href "deletePDevice", title:"Delete $state.currentDisplayName", description: ""
        }
   }
}

def manuallyAdd(){
   dynamicPage(name: "manuallyAdd", title: "Manually add a Milight bulb", nextPage: "manuallyAddConfirm") {
		section {
			paragraph "This process will manually create a Milight device based on the entered IP address. The SmartApp needs to then communicate with the device to obtain additional information from it."
            input "deviceType", "enum", title:"Device Type", description: "", required: false, options: ["Milight RGBW","Milight CCT+W"]
            input "ipAddress", "text", title:"IP Address", description: "", required: false 
		}
    }
}

def manuallyAddConfirm(){
   if ( ipAddress =~ /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$/) {
       log.debug "Creating Milight bulb with dni: ${convertIPtoHex(ipAddress)}:${convertPortToHex("80")}"
       addChildDevice("fireyprotons", deviceType ? deviceType : "Milight Bulb", "${convertIPtoHex(ipAddress)}:${convertPortToHex("80")}", location.hubs[0].id, [
           "label": (deviceType ? deviceType : "Milight Bulb") + " (${ipAddress})",
           "data": [
           "ip": ipAddress,
           "port": "80" 
           ]
       ])
   
       app.updateSetting("ipAddress", "")
            
       dynamicPage(name: "manuallyAddConfirm", title: "Manually add a Milight bulb", nextPage: "mainPage") {
		   section {
			   paragraph "The bulb has been added. Press next to return to the main page."
	    	}
       }
    } else {
        dynamicPage(name: "manuallyAddConfirm", title: "Manually add a Milight bulb", nextPage: "mainPage") {
		    section {
			    paragraph "The entered ip address is not valid. Please try again."
		    }
        }
    }
}

def deletePDevice(){
    try {
        unsubscribe()
        deleteChildDevice(state.currentDeviceId)
        dynamicPage(name: "deletePDevice", title: "Deletion Summary", nextPage: "mainPage") {
            section {
                paragraph "The bulb has been deleted. Press next to continue"
            } 
        }
    
	} catch (e) {
        dynamicPage(name: "deletePDevice", title: "Deletion Summary", nextPage: "mainPage") {
            section {
                paragraph "Error: ${(e as String).split(":")[1]}."
            } 
        }
    
    }
}

def changeName(){
    def thisDevice = getChildDevice(state.currentDeviceId)
    thisDevice.label = settings["${state.currentDeviceId}_label"]

    dynamicPage(name: "changeName", title: "Change Name Summary", nextPage: "mainPage") {
	    section {
            paragraph "The device has been renamed. Press \"Next\" to continue"
        }
    }
}

def getVerifiedDevices() {
	getDevices().findAll{ it?.value?.verified == true }
}

def configured() {
	
}

def buttonConfigured(idx) {
	return settings["lights_$idx"]
}

def isConfigured(){
   if(getChildDevices().size() > 0) return true else return false
}

def isVirtualConfigured(did){ 
    def foundDevice = false
    getChildDevices().each {
       if(it.deviceNetworkId != null){
       if(it.deviceNetworkId.startsWith("${did}/")) foundDevice = true
       }
    }
    return foundDevice
}

private virtualCreated(number) {
    if (getChildDevice(getDeviceID(number))) {
        return true
    } else {
        return false
    }
}

private getDeviceID(number) {
    return "${state.currentDeviceId}/${app.id}/${number}"
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
    unschedule()
	initialize()
}


void verifyDevices() {
    def devices = getDevices().findAll { it?.value?.verified != true }
    devices.each {
        def ip = convertHexToIP(it.value.networkAddress)
        def port = convertHexToInt(it.value.deviceAddress)
        String host = "${ip}:${port}"
        sendHubCommand(new physicalgraph.device.HubAction("""GET ${it.value.ssdpPath} HTTP/1.1\r\nHOST: $host\r\n\r\n""", physicalgraph.device.Protocol.LAN, host, [callback: deviceDescriptionHandler]))
    }
}

def getDevices() {
    state.devices = state.devices ?: [:]
}

void deviceDescriptionHandler(physicalgraph.device.HubResponse hubResponse) {
	log.trace "description.xml response (application/xml)"
	def body = hubResponse.xml
    log.debug body?.device?.friendlyName?.text()
	if (body?.device?.modelName?.text().startsWith("Milight")) {
		def devices = getDevices()
		def device = devices.find {it?.key?.contains(body?.device?.UDN?.text())}
		if (device) {
			device.value << [name:body?.device?.friendlyName?.text() + " (" + convertHexToIP(hubResponse.ip) + ")", serialNumber:body?.device?.serialNumber?.text(), verified: true]
		} else {
			log.error "/description.xml returned a device that didn't exist"
		}
	}
}

def addDevices() {
    def devices = getDevices()
    def sectionText = ""

    selectedDevices.each { dni ->bridgeLinking
        def selectedDevice = devices.find { it.value.mac == dni }
        def d
        if (selectedDevice) {
            d = getChildDevices()?.find {
                it.deviceNetworkId == selectedDevice.value.mac
            }
        }

        if (!d) {
            log.debug selectedDevice
            log.debug "Creating Milight Bulb with dni: ${selectedDevice.value.mac}"

            def deviceHandlerName
            if (selectedDevice?.value?.name?.startsWith("Milight RG"))
                deviceHandlerName = "Milight RGBW"
            else (selectedDevice?.value?.name?.startsWith("Milight CC"))
                deviceHandlerName = "Milight CCT+W"
            def newDevice = addChildDevice("fireyprotons", deviceHandlerName, selectedDevice.value.mac, selectedDevice?.value.hub, [
                "label": selectedDevice?.value?.name ?: "Milight Bulb",
                "data": [
                    "mac": selectedDevice.value.mac,
                    "ip": convertHexToIP(selectedDevice.value.networkAddress),
                    "port": "" + Integer.parseInt(selectedDevice.value.deviceAddress,16)
                ]
            ])
            sectionText = sectionText + "Succesfully added Milight bulb with ip address ${convertHexToIP(selectedDevice.value.networkAddress)} \r\n"
        }
        
	} 
    log.debug sectionText
        return dynamicPage(name:"addDevices", title:"Devices Added", nextPage:"mainPage",  uninstall: true) {
        if(sectionText != ""){
		section("Add Milight Results:") {
			paragraph sectionText
		}
        }else{
        section("No devices added") {
			paragraph "All selected devices have previously been added"
		}
        }
}
    }

def uninstalled() {
    unsubscribe()
    getChildDevices().each {
        deleteChildDevice(it.deviceNetworkId)
    }
}



private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04x', port.toInteger() )
    return hexport
}
