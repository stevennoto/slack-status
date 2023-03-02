package com.simplyautomatic.slack.status


import com.google.gson.Gson
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
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
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
					val splitBy = args?.getOptionValues("split-by")?.first() ?: ""
					val splitByInterval = Interval.values().firstOrNull { it.name == splitBy.uppercase() }
					if (channelId == null || startDate == null || endDate == null) {
						throw Exception("Channel and start/end dates must be provided to get stats.")
					}
					if (splitBy.isNotBlank() && splitByInterval == null) {
						throw Exception("Channel stats can only be split-by 'week' or 'month', not '$splitBy'.")
					}

					val channelStats = getChannelStats(slackApiToken, channelId, startDate, endDate, splitByInterval)
					printChannelStats(channelStats)
				}
			}
		} catch (e: Exception) {
			println("Error: ${e.message}")
e.printStackTrace()
			exitProcess(1)
		}

		exitProcess(0)
	}

	fun getPrettyIntervalNameFromSlackTimestamp(timestamp: String, interval: Interval): String {
		val date = Date(timestamp.substringBefore(".").toLong() * 1000)
		return when (interval) {
			Interval.WEEK -> {
				val startOfWeek = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
					.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
				startOfWeek.format(DateTimeFormatter.ofPattern("'Week of 'YYYY/MM/dd"))
			}

			Interval.MONTH -> {
				SimpleDateFormat("'Month of 'YYYY/MM").format(date)
			}
		}
	}

//	fun median(list: List<Double>): Double {
//		return list.sorted().let {
//			if (it.size % 2 == 0)
//				(it[it.size / 2] + it[(it.size - 1) / 2]) / 2
//			else
//				it[it.size / 2]
//		}
//	}

//					println("median " + median(listOf(1.0, 2.0)))
//					println("median " + median(listOf(1.0, 3.0, 2.0)))
//					println("outlier " + removeOutliers(listOf(1.0, 3.0, 2.0)))
//					println("outlier " + removeOutliers(listOf(1.0, 3.0, 2.0, 2.0, 2.0, 2.0, 82.5)))
//	fun removeOutliers(nums: List<Double>) : List<Double> {
//		val first = nums.subList(0, (nums.size + 1) / 2)
//		val second = nums.subList((nums.size + 1) / 2, nums.size)
//		val quartile1: Double = median(first)
//		val quartile3: Double = median(second)
//		val iqr = quartile3 - quartile1
//		val lowerFence = quartile1 - 1.5 * iqr
//		val upperFence = quartile3 + 1.5 * iqr
//		return nums.filter { it >= lowerFence && it <= upperFence }.toMutableList()
//	}

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
			|Specify `--channel-id='Slack channel ID' --start='date/time' --end='date/time'` when getting stats, and optionally `--split-by='week|month'`.""".trimMargin())
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

	fun getChannelStats(apiToken: String?, channelId: String, startDate: Date, endDate: Date, interval: Interval?): List<MessageStats> {
		val client = getSlackClient(apiToken)

		val request = ConversationsHistoryRequest.builder()
			.channel(channelId)
			.oldest((startDate.time / 1000).toString())
			.latest((endDate.time / 1000).toString())
			.limit(100)
			.build()

		var messageStats = MessageStats(if (interval == null) "$startDate to $endDate" else "")
		val messagesStatsList = mutableListOf(messageStats)

		do {
			val response = client.conversationsHistory(request)
			if (!response.isOk) throw Exception("Slack API returned error ${response.error ?: response.warning}")
			request.cursor = response.responseMetadata?.nextCursor // Use cursor to track pagination

			response.messages.forEach {
				// Split by interval if specified
				if (interval != null) {
					val messagePeriodName = getPrettyIntervalNameFromSlackTimestamp(it.ts, interval)
					if (messageStats.periodName.isEmpty()) messageStats.periodName = messagePeriodName
					if (messageStats.periodName != messagePeriodName) {
						messageStats = MessageStats(messagePeriodName)
						messagesStatsList += messageStats
					}
				}

				messageStats.numMessages++
				if (!it.threadTs.isNullOrBlank() && it.subtype != "thread_broadcast") {
					messageStats.numThreads++
					messageStats.threadsStats.add(ThreadStats(it.replyCount, it.replyUsers.size))
				}
			}
		} while (request.cursor?.isNotEmpty() == true)

		messagesStatsList.sortBy { it.periodName }

		return messagesStatsList
	}

	fun printChannelStats(stats: List<MessageStats>) {
		println("Period,Num Messages,Num Threads,Min Thread Length,Max Thread Length,Avg Thread Length,Avg Thread Users")
//TODO do we need to sort?
		stats.forEach {
			val minThreadLength = it.threadsStats.minOfOrNull { it.numMessages }
			val maxThreadLength = it.threadsStats.maxOfOrNull { it.numMessages }
			val avgThreadLength = String.format("%.2f", it.threadsStats.map { it.numMessages }.average())
//			val avgThreadLengthNoOutliers = String.format("%.2f", removeOutliers(it.threadsNumMessages.map { it.toDouble() }).average())
			val avgThreadUsers = String.format("%.2f", it.threadsStats.map { it.numUsers }.average())
			println("${it.periodName},${it.numMessages},${it.numThreads},${minThreadLength},${maxThreadLength},${avgThreadLength},${avgThreadUsers}")
//			|${it.keywords.toSortedMap()}""")
		}
	}

//	fun getChannelIdByName(apiToken: String?, channelName: String): String {
////					ConversationsListResponse conversationsList(ConversationsListRequest req) throws IOException, SlackApiException;
////					ConversationsListResponse conversationsList(RequestConfigurator<ConversationsListRequest.ConversationsListRequestBuilder> req) throws IOException, SlackApiException;
////					ConversationsHistoryResponse conversationsHistory(ConversationsHistoryRequest req) throws IOException, SlackApiException;
////					ConversationsHistoryResponse conversationsHistory(RequestConfigurator<ConversationsHistoryRequest.ConversationsHistoryRequestBuilder> req) throws IOException, SlackApiException;
//
//
//		val client = getSlackClient(apiToken)
//		val request = ConversationsListRequest.builder().build()
//// TODO QQQ where to specify search name
//		request.channelNameOrSomething = channelName
//		request.isExcludeArchived = false
//		val response = client.conversationsList(request)
//		if (!response.isOk) throw Exception("Slack API returned error ${response.error ?: response.warning}")
//// TODO QQQ how to get one channel out, throw excep if none
////					response2.channels.forEach {
////						println(it.id + ": " + it.name)
////					}
////					println(response2.channels)
//		return response.channels
//	}
}

fun main(args: Array<String>) {
	runApplication<SlackStatusApplication>(*args)
}
