# Slack Status

This is a simple app to help get or set your Slack status from the command line.

The app is a Spring Boot app written in Kotlin. It uses the [Java Slack API client](https://github.com/slackapi/java-slack-sdk#slack-api-client) 
to read and set status info.

## Setup

To use this app, you need a ["user token"](https://api.slack.com/authentication/token-types#user) for the Slack API. 
This allows the app to take actions on your behalf. User token strings begin with `xoxp-`.

To create a user token, first create a Slack app, optionally [following this tutorial](https://api.slack.com/tutorials/tracks/getting-a-token). 
Then, find you app at https://api.slack.com/apps/ and click "OAuth & Permissions." Look under "User Token Scopes" and 
add scopes for `users.profile:read` and `users.profile:write`. Then reinstall the app when prompted, which will 
regenerate tokens. Then copy your "User OAuth Token" from the page.

You can then specify your token in an environment variable named `$SLACK_API_TOKEN` or with a `token` argument.

## Usage

Run the app with Java, for example, `java -jar slack-status.jar`.

Set your Slack API user token via environment variable `SLACK_API_TOKEN` or as `--token=<token>`.

Specify `--get-status`, `--set-status`, `--clear-status`, or `--help` for mode.

Specify `--text='some text' --emoji='emoji-name'` when setting status.

Optionally specify `--expires='some date/time expression'` when setting status.
[Natty](https://github.com/joestelmach/natty) is used for natural language date parsing, so expressions like 
"tomorrow morning" or "wednesday at 5pm" will be recognized.

## TODO

- [x] Get token from env or arg
- [x] Improve results display
- [x] Add mode to display status
- [x] Add mode to clear status (empty strings to status_text and status_emoji)
- [x] Add 'help'/'usage' to cli and README
- [x] Add setting of expiration date, with shortcuts like "end of day"
- [ ] Add saving/retrieving template statuses
- [ ] Decide whether to keep Spring dependencies
