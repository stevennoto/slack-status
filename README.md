# Slack Status

This is a simple app to help set one's Slack status from the command line.

The app is a Spring Boot app written in Kotlin. It uses the [Java Slack API client](https://github.com/slackapi/java-slack-sdk#slack-api-client) 
to read and set status info.

## TODO

- [ ] Get token from env, props, or arg
- [ ] Add mode to display status
- [ ] Add usage info to README
- [ ] Add usage printing to cli
- [ ] Add setting of expiration date, with shortcuts like "end of day"
- [ ] Add saving/retrieving template statuses
