package com.simplyautomatic.slack.status


import com.joestelmach.natty.Parser
import com.slack.api.Slack
import com.slack.api.methods.MethodsClient
import com.slack.api.methods.request.conversations.ConversationsHistoryRequest
import com.slack.api.methods.request.users.profile.UsersProfileGetRequest
import com.slack.api.methods.request.users.profile.UsersProfileSetRequest
import com.slack.api.model.User.Profile
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

@SpringBootApplication
class SlackStatusApplication : ApplicationRunner {

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
					val statusExpiration = args?.getOptionValues("expires")?.first()

					if (statusText == null || statusEmoji == null) {
						throw Exception("Status text and emoji must be provided to set status.")
					}

					val statusEmojiQuoted =
						if (statusEmoji.startsWith(":") && statusEmoji.endsWith(":")) statusEmoji else ":$statusEmoji:"

					val expirationDate = Parser().parse(statusExpiration ?: "").flatMap { it.dates }.firstOrNull()

					val profile = setStatus(slackApiToken, statusText, statusEmojiQuoted, expirationDate)
					printStatus(profile)
				}
				Mode.CLEAR_STATUS -> {
					println("Clearing Slack status...")
					setStatus(slackApiToken, "", "")
					println("Slack status cleared.")
				}
				Mode.GET_CHANNEL_STATS -> {
					println("Getting Slack channel stats...")

					val channelId = args?.getOptionValues("channel-id")?.first()
					val start = args?.getOptionValues("start")?.first()
					val end = args?.getOptionValues("end")?.first()
					val startDate = Parser().parse(start ?: "").flatMap { it.dates }.firstOrNull()
					val endDate = Parser().parse(end ?: "").flatMap { it.dates }.firstOrNull()
					if (channelId == null || startDate == null || endDate == null) {
						throw Exception("Channel and start/end dates must be provided to get stats.")
					}

					val channelStats = getChannelStats(slackApiToken, channelId, startDate, endDate)
					printChannelStats(listOf(channelStats))
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
		if (args.contains("get-channel-stats")) return Mode.GET_CHANNEL_STATS
		return Mode.USAGE
	}

	fun printUsage() {
		println("""Slack Status application: get, set, or clear your Slack status!
			|Set your Slack API user token via environment variable `SLACK_API_TOKEN` or as `--token=<token>`.
			|Specify `--get-status`, `--set-status`, `--clear-status`, `--get-channel-stats`, or `--help` for mode.
			|Specify `--text='some text' --emoji='emoji-name'` when setting status, and optionally `--expires='date/time'`
			|Specify `--channel-id='Slack channel ID' --start='date/time' --end='date/time'` when getting stats.""".trimMargin())
	}

	fun getSlackClient(apiToken: String?): MethodsClient {
		if (apiToken.isNullOrEmpty()) {
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

	fun setStatus(apiToken: String?, statusText: String, statusEmoji: String, statusExpiration: Date? = null): Profile {
		val client = getSlackClient(apiToken)
		val profile = Profile()
		profile.statusText = statusText
		profile.statusEmoji = statusEmoji
		if (statusExpiration != null) {
			profile.statusExpiration = statusExpiration.time / 1000
		}
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

	fun getChannelStats(apiToken: String?, channelId: String, startDate: Date, endDate: Date): MessageStats {
		val client = getSlackClient(apiToken)

		val request = ConversationsHistoryRequest.builder()
			.channel(channelId)
			.oldest((startDate.time / 1000).toString())
			.latest((endDate.time / 1000).toString())
			.limit(100)
			.build()

		val messageStats = MessageStats("$startDate to $endDate")
		do {
			val response = client.conversationsHistory(request)
			if (!response.isOk) throw Exception("Slack API returned error ${response.error ?: response.warning}")
			request.cursor = response.responseMetadata?.nextCursor // Use cursor to track pagination

			response.messages.forEach {
				messageStats.numMessages++
				if (!it.threadTs.isNullOrBlank() && it.subtype != "thread_broadcast") {
					messageStats.numThreads++
					messageStats.threadsStats.add(ThreadStats(it.replyCount, it.replyUsers.size))
				}
			}
		} while (request.cursor?.isNotEmpty() == true)
		return messageStats
	}

	fun printChannelStats(stats: List<MessageStats>) {
		println("Period,Num Messages,Num Threads,Min Thread Length,Max Thread Length,Avg Thread Length,Avg Thread Users")
		stats.forEach {
			val minThreadLength = it.threadsStats.minOfOrNull { it.numMessages }
			val maxThreadLength = it.threadsStats.maxOfOrNull { it.numMessages }
			val avgThreadLength = String.format("%.2f", it.threadsStats.map { it.numMessages }.average())
			val avgThreadUsers = String.format("%.2f", it.threadsStats.map { it.numUsers }.average())
			println("${it.periodName},${it.numMessages},${it.numThreads},${minThreadLength},${maxThreadLength},${avgThreadLength},${avgThreadUsers}")
		}
	}
}

fun main(args: Array<String>) {
	runApplication<SlackStatusApplication>(*args)
}
