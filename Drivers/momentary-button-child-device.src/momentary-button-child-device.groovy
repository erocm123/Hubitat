/**
 *  Momentary Button Child Device
 *
 *  Copyright 2017 Eric Maycock
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
metadata {
	definition (name: "Momentary Button Child Device", namespace: "erocm123", author: "Ben Deitch") {
        capability "Actuator"
		capability "Momentary"
		capability "PushableButton"
        capability "ReleasableButton"
		capability "Sensor"
	}
}

def installed() {
	sendEvent(name: "numberOfButtons", value: 1)
}

def push() {
    state.pushed = true
    sendEvent(name: "pushed", value: 1, isStateChange: true)
}

def release() {
    if (!state.pushed) {
    	// sometimes we get a released event from the Qubino without the pushed event
        sendEvent(name: "pushed", value: 1, isStateChange: true)
    }
    sendEvent(name: "released", value: 1, isStateChange: true)
    state.pushed = false
}