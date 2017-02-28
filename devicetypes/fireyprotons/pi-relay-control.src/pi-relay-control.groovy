/**
 *  Pi Device GPIO Relay Controller
 *
 *  Copyright 2017 FireyProtons
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
 */

preferences {
	input("ip", "text", title: "IP", description: "The ip of your raspberry pi i.e. 192.168.1.110")
	input("port", "text", title: "Port", description: "The port your Apache service is running on. The default is 80", default: "80")
	input("gpio", "text", title: "GPIO#", description: "The GPIO pin your relay is connected to")
} 

metadata {
	definition (name: "Pi Relay Control", namespace: "FireyProtons", author: "FireyProtons") {
		capability "Switch"
		capability "Refresh"
		capability "Polling"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles {
		standardTile("switch", "device.switch", width: 1, height: 1, canChangeIcon: true) {
			state "on", label:'Closed', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#79b821"
			state "off", label:'Open', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ff0000"
		}
                
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 1, height: 1) {
			state("default", label:'refresh', action:"polling.poll", icon:"st.secondary.refresh-icon")
		}

		main "switch"
		details (["switch", "refresh"])
	}
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
	def msg = parseLanMessage(description)
	log.info "Return data: " + msg.header
    
    def response = msg.body
    def strResponse = response[0..response.length()-3]
    log.debug strResponse + " is the only one for me."
    
	// We need to update the UI with the state
    if(strResponse == 'closed' || strResponse == 'open'){
    	log.debug "GPIO state response"
		setUI(strResponse)
    }
}

def poll() {
	log.debug "Executing 'poll'"   
        
	storeNetworkDeviceId()
    updateGpioState()

}

def refresh() {
	log.debug "Executing 'refresh'"
    
	poll();
}

def on() {
	log.debug "Executing 'on'"
    
    setDeviceState('closed')
}

def off() {
	log.debug "Executing 'off'"
    
    setDeviceState('open')
}

def setDeviceState(state) {
	log.debug "Executing 'setDeviceState'"
    
  	def Path = "/php/toggle.php"
	//Path += (state == "on") ? "1" : "0";
    
  	executeRequest(Path, "POST", false)
	//setUI(state)
}

def setUI(strResponse){
	   
    log.debug "Garage current state: " + strResponse   
    
    def switchState = strResponse == "closed" ? "on" : "off";
    log.debug "New state is: " + switchState
    
    sendEvent(name: "switch", value: switchState)
}

def updateGpioState(){
	
    executeRequest("/php/status.php", "GET", false);
}

def executeRequest(Path, method, overridePinDirectionCheck) {
		
	//log.debug "The " + method + " path is: " + Path;
	
	storeNetworkDeviceId()
    
    def headers = [:] 
    headers.put("HOST", "$settings.ip:$settings.port")
    
    try {    	
    
        def actualAction = new physicalgraph.device.HubAction(
            method: method,
            path: Path,
            headers: headers,
            query: [data: "value1"])
        	
        def hubAction = [delayAction(100), actualAction]
        
   		return hubAction
    }
    catch (Exception e) {
        log.debug "Hit Exception $e on $hubAction"
    }
}

private delayAction(long time) {
	//log.debug "Delaying by: " + time
    
	new physicalgraph.device.HubAction("delay $time")
}

private storeNetworkDeviceId(){
    def iphex = convertIPtoHex(settings.ip).toUpperCase()
    def porthex = convertPortToHex(settings.port)
    device.deviceNetworkId = "$iphex:$porthex" 
    
    //log.debug device.deviceNetworkId
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    //log.debug "IP address entered is $ipAddress and the converted hex code is $hex"
    return hex

}

private String convertPortToHex(port) {
    String hexport = port.toString().format( '%04x', port.toInteger() )
    //log.debug hexport
    return hexport
}