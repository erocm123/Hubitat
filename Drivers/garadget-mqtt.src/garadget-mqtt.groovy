/**
 *  Copyright 2020 Eric Maycock
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
 *  Garadget MQTT Driver For Hubitat
 *
 *  Author: Eric Maycock (erocm123)
 *  Date: 2020-06-09
 */

import groovy.json.JsonSlurper

metadata {
    definition (
        name: "Garadget MQTT",
        namespace: "erocm123",
        author: "Eric Maycock",
        importUrl: "https://raw.githubusercontent.com/erocm123/Hubitat/master/Drivers/garadget-mqtt.src/garadget-mqtt.groovy"
    )
    {
        capability "Switch"
        capability "Initialize" 
        capability "Contact Sensor"
        capability "Signal Strength"
        capability "Actuator"
        capability "Sensor"
        capability "GarageDoorControl"

        attribute "reflection", "string"
        attribute "status", "string"
        attribute "time", "string"
        attribute "lastAction", "string"
        attribute "reflection", "string"
        attribute "ver", "string"
        
        command "stop"
    }

    preferences {
	    input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true
	    input name: "MQTTBrokerPort", type: "text", title: "MQTT Broker Port:", required: true
	    input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false
	    input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false
	    input name: "deviceid", type: "text", title: "Garadget Device Name:", description: "", required: true
	    input name: "debugEnable", type: "bool", title: "Enable logging", required: true, defaultValue: true
    }
}

def installed() {
    log.warn "installed..."
}

def parse(String description) {
	Date date = new Date(); 
	topic = interfaces.mqtt.parseMessage(description).topic
	topic = topic.substring(topic.lastIndexOf("/") + 1)
	payload = interfaces.mqtt.parseMessage(description).payload
    if (debugEnable) log.debug "${device.label?device.label:device.name}: Topic - ${topic}"
	if (debugEnable) log.debug "${device.label?device.label:device.name}: Payload - ${payload}"
    def slurper = new JsonSlurper()
    def result = slurper.parseText(payload)
	if(topic == "status") {
        sendEvent(name: 'status', value: result.status)
        if(result.status == "open" || result.status == "closed"){
        	sendEvent(name: 'contact', value: result.status, displayed: false)
            sendEvent(name: 'door', value: result.status, displayed: false)
        }
        sendEvent(name: 'lastAction', value: result.time, displayed: false)
        sendEvent(name: 'reflection', value: result.sensor, displayed: false)
        sendEvent(name: 'rssi', value: result.signal, displayed: false)
    }
    if(topic == "config") {
        sendEvent(name: 'ver', value: result.ver, displayed: false)
        if (debugEnable) log.debug "${device.label?device.label:device.name}: Firmware Version: "+result.ver
        if (debugEnable) log.debug "${device.label?device.label:device.name}: Sensor Scan Interval (ms): "+result.rdt 
        if (debugEnable) log.debug "${device.label?device.label:device.name}: Door Moving Time (ms): "+result.mtt 
        if (debugEnable) log.debug "${device.label?device.label:device.name}: Button Press Time (ms): "+result.rlt 
        if (debugEnable) log.debug "${device.label?device.label:device.name}: Delay Between Consecutive Button Presses (ms): "+result.rlp 
        if (debugEnable) log.debug "${device.label?device.label:device.name}: Reflection threshold below which the door is considered open: "+result.srt 
        if (debugEnable) log.debug "${device.label?device.label:device.name}: MQTT Topic: "+result.nme 
        if (debugEnable) log.debug "${device.label?device.label:device.name}: MQTT IP Address: "+result.mqip
        if (debugEnable) log.debug "${device.label?device.label:device.name}: MQTT Port: "+result.mqpt
        if (debugEnable) log.debug "${device.label?device.label:device.name}: MQTT User (If Set): "+result.mqus
    }
}

def updated() {
    if (debugEnable) log.info "updated..."
    unschedule()
    initialize()
}

def logsOff(){
    log.warn "Debug logging disabled."
    device.updateSetting("debugEnable",[value:"false",type:"bool"])
}

def uninstalled() {
    if (debugEnable) log.info "${device.label?device.label:device.name}: disconnecting from mqtt"
    alphaV1mqttDisconnect(device)
}

def initialize() {
	if (debugEnable) runIn(3600,logsOff)
	try {
        mqttbroker = "tcp://" + settings?.MQTTBroker + ":" + settings?.MQTTBrokerPort
        interfaces.mqtt.connect(mqttbroker, "hubitat-${location.name.toLowerCase()}", settings?.username,settings?.password)
        state.MTQQbrokerURL= "${mqttbroker}"
        //give it a chance to start
        pauseExecution(1000)
        if (debugEnable) log.debug "${device.label?device.label:device.name}: Connection established"
		if (debugEnable) log.debug "${device.label?device.label:device.name}: Subscribed to: ${"garadget/" + settings?.deviceid + "/#"}"
        interfaces.mqtt.subscribe("garadget/" + settings?.deviceid + "/#")
    } catch(e) {
        if (debugEnable) log.debug "${device.label?device.label:device.name}: Initialize error: ${e.message}"
    }
    schedule('0 */15 * ? * *', watchDog)
}

def watchDog() {
    if (debugEnable) log.debug "${device.label?device.label:device.name}: Checking MQTT status"
    if (debugEnable) log.debug "${device.label?device.label:device.name}: MQTT Connected: (${interfaces.mqtt.isConnected()})"
    if(!interfaces.mqtt.isConnected()) initialize()
}

def mqttClientStatus(String status) {
    log.info "${device.label?device.label:device.name}: MQTT ${status}"
}

def on() {
	if (debugEnable) log.debug "${device.label?device.label:device.name}: Executing - on()"
	interfaces.mqtt.publish("garadget/Garage/command", "open")
}

def off() {
	if (debugEnable) log.debug "${device.label?device.label:device.name}: Executing - off()"
	interfaces.mqtt.publish("garadget/Garage/command", "close")
}

def stop(){
	if (debugEnable) log.debug "${device.label?device.label:device.name}: Executing - stop()"
    interfaces.mqtt.publish("garadget/Garage/command", "stop")
}

def openCommand(){
	if (debugEnable) log.debug "Executing - openCommand() - 'on'"
    on()
}

def closeCommand(){
	if (debugEnable) log.debug "Executing - closeCommand() - 'off'"
	off()
}

def open() {
	if (debugEnable) log.debug "Executing - open() - 'on'"
	on()
}

def close() {
	if (debugEnable) log.debug "Executing - close() - 'off'"
	off()
}
