package com.simplyautomatic.slack.status


import com.slack.api.Slack
import com.slack.api.methods.MethodsClient
import com.slack.api.methods.request.users.profile.UsersProfileGetRequest
import com.slack.api.model.User.Profile
import com.slack.api.methods.request.users.profile.UsersProfileSetRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.system.exitProcess

@SpringBootApplication
class SlackStatusSetterApplication : ApplicationRunner {

	@Value("\${SLACK_API_TOKEN:}")
	val slackApiTokenFromEnv: String? = null

	override fun run(args: ApplicationArguments?) {
		val mode = getMode(args?.optionNames?.toList())

		val slackApiToken = args?.getOptionValues("token")?.first() ?: slackApiTokenFromEnv

		try {
			when (mode) {
				Mode.USAGE -> {
					printUsage()
				}
				Mode.GET_STATUS -> {
					println("Getting Slack status...")
					val profile = getStatus(slackApiToken)
					printStatus(profile)
				}
				Mode.SET_STATUS -> {
					println("Setting Slack status...")

					val statusText = args?.getOptionValues("text")?.first()
					val statusEmoji = args?.getOptionValues("emoji")?.first()

					if (statusText == null || statusEmoji == null) {
						throw Exception("Status text and emoji must be provided to set status.")
					}

					val statusEmojiQuoted =
						if (statusEmoji.startsWith(":") && statusEmoji.endsWith(":")) statusEmoji else ":$statusEmoji:"

					val profile = setStatus(slackApiToken, statusText, statusEmojiQuoted)
					printStatus(profile)
				}
				Mode.CLEAR_STATUS -> {
					println("Clearing Slack status...")
					setStatus(slackApiToken, "", "")
					println("Slack status cleared.")
				}
			}
		} catch (e: Exception) {
			println("Error: ${e.message}")
			exitProcess(1)
		}

		exitProcess(0)
	}

	fun getMode(args: List<String>?): Mode {
		if (args == null || args.contains("usage") || args.contains("help")) return Mode.USAGE
		if (args.contains("clear-status")) return Mode.CLEAR_STATUS
		if (args.contains("get-status")) return Mode.GET_STATUS
		if (args.contains("set-status")) return Mode.SET_STATUS
		return Mode.USAGE
	}

	fun printUsage() {
		// TODO
		println("Usage: TODO")
	}

	fun getSlackClient(apiToken: String?): MethodsClient {
		if (apiToken == null || apiToken.isEmpty()) {
			throw Exception("Slack API token not found. Please specify API token by environment variable or argument.")
		}

		return Slack.getInstance().methods(apiToken)
	}

	fun getStatus(apiToken: String?): Profile  {
		val client = getSlackClient(apiToken)
		val request = UsersProfileGetRequest.builder().build()
		val response = client.usersProfileGet(request)
		if (!response.isOk) throw Exception("Slack API returned error ${response.error ?: response.warning}")
		return response.profile
	}

	fun setStatus(apiToken: String?, statusText: String, statusEmoji: String): Profile {
		val client = getSlackClient(apiToken)
		val profile = Profile()
		profile.statusText = statusText
		profile.statusEmoji = statusEmoji
		val request = UsersProfileSetRequest.builder().profile(profile).build()
		val response = client.usersProfileSet(request)
		if (!response.isOk) throw Exception("Slack API returned error ${response.error ?: response.warning}")
		return response.profile
	}

	fun printStatus(profile: Profile) {
		println("Status for ${profile.displayName}:")
		println("\tText: ${profile.statusText}")
		println("\tEmoji: ${profile.statusEmoji}")
		if (profile.statusExpiration != null &&  profile.statusExpiration > 0) {
			val expirationDate = Date(profile.statusExpiration * 1000)
			val expirationDisplay = SimpleDateFormat("yyyy-MM-dd HH:mm").format(expirationDate)
			println("\tStatus expires: $expirationDisplay")
		}
	}
}

fun main(args: Array<String>) {
	runApplication<SlackStatusSetterApplication>(*args)
}
