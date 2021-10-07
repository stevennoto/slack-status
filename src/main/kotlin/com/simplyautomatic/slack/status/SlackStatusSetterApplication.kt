package com.simplyautomatic.slack.status


import com.slack.api.Slack
import com.slack.api.model.User.Profile
import com.slack.api.methods.request.users.profile.UsersProfileSetRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
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

		// Get desired status info
		val statusText = args?.getOptionValues("text")?.first()
		val statusEmoji = args?.getOptionValues("emoji")?.first()

		if (statusText == null || statusEmoji == null) {
			println("Error: Must provide both text and an emoji for status.")
			exitProcess(1)
		}

		val statusEmojiQuoted =
			if (statusEmoji.startsWith(":") && statusEmoji.endsWith(":")) statusEmoji else ":$statusEmoji:"

		val client = Slack.getInstance().methods(slackApiToken)
		val profile = Profile()
		profile.statusText = statusText
		profile.statusEmoji = statusEmojiQuoted
		val request4 = UsersProfileSetRequest.builder().profile(profile).build()
		val response4 = client.usersProfileSet(request4)
		println(response4)

		exitProcess(0)
	}
}

fun main(args: Array<String>) {
	runApplication<SlackStatusSetterApplication>(*args)
}
