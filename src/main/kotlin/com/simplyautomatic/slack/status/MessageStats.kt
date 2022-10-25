package com.simplyautomatic.slack.status

data class MessageStats(val periodName: String) {
	var numMessages = 0
	var numThreads = 0
	val threadsStats = mutableListOf<ThreadStats>()
}