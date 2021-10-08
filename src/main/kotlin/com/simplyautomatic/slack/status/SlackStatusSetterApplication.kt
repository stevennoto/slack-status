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
		// Get Slack API token from arg or env
		val slackApiToken = args?.getOptionValues("token")?.first() ?: slackApiTokenFromEnv

		if (slackApiToken == null || slackApiToken.isEmpty()) {
			println("Error: Must provide Slack API token.")
			exitProcess(1)
		}

		val client = Slack.getInstance().methods(slackApiToken)

		// Get desired status info
		val statusText = args?.getOptionValues("text")?.first()
		val statusEmoji = args?.getOptionValues("emoji")?.first()

		if (statusText == null || statusEmoji == null) {
			// TODO: add mode choice param instead of inferring it from lack of status text/emoji
			getProfile(client)
			exitProcess(0)
		}

		val statusEmojiQuoted =
			if (statusEmoji.startsWith(":") && statusEmoji.endsWith(":")) statusEmoji else ":$statusEmoji:"

		setProfile(client, statusText, statusEmojiQuoted)

		exitProcess(0)
	}

	fun getProfile(client: MethodsClient)  {
		println("Getting Slack status...")
		val request = UsersProfileGetRequest.builder().build()
		val response = client.usersProfileGet(request)
		if (response.isOk) {
			printProfileStatus(response.profile)
		} else {
			println("Error: ${response.error ?: response.warning}")
			exitProcess(1)
		}
	}

	fun setProfile(client: MethodsClient, statusText: String, statusEmoji: String) {
		println("Setting Slack status...")
		val profile = Profile()
		profile.statusText = statusText
		profile.statusEmoji = statusEmoji
		val request = UsersProfileSetRequest.builder().profile(profile).build()
		val response = client.usersProfileSet(request)
		if (response.isOk) {
			printProfileStatus(response.profile)
		} else {
			println("Error: ${response.error ?: response.warning}")
			exitProcess(1)
		}
	}

	fun printProfileStatus(profile: Profile) {
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
