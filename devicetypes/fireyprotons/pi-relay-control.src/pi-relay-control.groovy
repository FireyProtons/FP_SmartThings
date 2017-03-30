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
 *  Some of the code taken from iBeech/SmartThings: https://github.com/iBeech/SmartThings
 */

preferences {
	input("ip", "text", title: "IP", description: "The ip of your raspberry pi i.e. 192.168.1.110")
	input("port", "text", title: "Port", description: "The port your HTTP service is running on. The default is 80", default: "80")
	input("gpio", "text", title: "GPIO#", description: "The GPIO pin your relay is connected to")
} 

metadata {
	definition (name: "Pi Relay Control", namespace: "FireyProtons", author: "FireyProtons") {
		capability "Actuator"
        capability "Sensor"
        capability "Garage Door Control"
		capability "Refresh"
		capability "Polling"
        
    }

	simulator {
				
	}
    
	tiles {
		standardTile("toggle", "device.door", width: 2, height: 2, canChangeIcon: false) {
			state("open", label:'${name}', action:"close", icon:"st.doors.garage.garage-open", backgroundColor:"#ffa81e", nextState:"closing")
			state("closed", label:'${name}', action:"open", icon:"st.doors.garage.garage-closed", backgroundColor:"#79b821", nextState:"opening")
            state("opening", label:'${name}', icon:"st.doors.garage.garage-opening", backgroundColor:"#ffe71e")
			state("closing", label:'${name}', icon:"st.doors.garage.garage-closing", backgroundColor:"#ffe71e")
            state("unknown", label:'${name}', action:"refresh", icon:"st.doors.garage.garage-unknown", backgroundColor:"$ffffff")
			
		}
		standardTile("open", "device.door", inactiveLabel: false, decoration: "flat") {
			state "default", label:'open', action:"open", icon:"st.doors.garage.garage-opening"
		}
		standardTile("close", "device.door", inactiveLabel: false, decoration: "flat") {
			state "default", label:'close', action:"close", icon:"st.doors.garage.garage-closing"
		}
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat") {
        	state "default", label: 'refresh', action:"refresh", icon: "st.secondary.refresh"
        }

		main "toggle"
		details(["toggle", "open", "close", "refresh"])
	}
}

// parse events into attributes
def parse(String description) {
	//log.debug "Parsing '${description}'"
	def msg = parseLanMessage(description)
	//log.info "Return data: " + msg.header
    def result = []
    def response = msg.body
    
    def strResponse = "nothing yet"
    if (response) {
    	strResponse = response[0..response.length()-3]
    }

	// We need to update the UI with the state
    if(strResponse == "nothing yet"){
        log.debug "error in response"
    }else{
        result << createEvent(name: "door", value: strResponse)
    }
    
    return result
}

def poll() {
//	log.debug "Executing 'poll'"   
        
	//storeNetworkDeviceId()
    updateGpioState()
}

def refresh() {
	log.debug "Executing 'refresh'"   
    poll()
}


def open() {
	log.debug "Executing 'open'"
  	def Path = "/php/garage.php?open=1"
//    log.debug = "executing " + Path
  	executeRequest(Path, "GET")
//    runIn(25, executeRequest(Path, "GET"))
}

def close() {
	log.debug "Executing 'close'"
  	def Path = "/php/garage.php?close=1"
//	log.debug = "executing " + Path
	executeRequest(Path, "GET")
//    runIn(15, executeRequest(Path, "GET")) 
}

def opening() {
	log.debug "Executing 'opening'"

}


def closing() {
	log.debug "Executing 'closing'"

}

def updateGpioState(){
    def Path = "/php/garage.php?status=1"
    executeRequest(Path, "GET");
}

def executeRequest(Path, method) {
	//log.debug "The " + method + " path is: " + Path;
	
	storeNetworkDeviceId()
    
    def headers = [:] 
    headers.put("HOST", "$settings.ip:$settings.port")
    
    try {    	
    
        def actualAction = new physicalgraph.device.HubAction(
            method: method,
            path: Path,
            headers: headers)

 //           log.debug actualAction
    	    def hubAction = [delayAction(10), actualAction]
 
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
