#!/bin/sh
cd "$(dirname "$0")"
java -cp out chat.client.ChatClient &
