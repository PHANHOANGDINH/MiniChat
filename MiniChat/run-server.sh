#!/bin/sh
cd "$(dirname "$0")"
java -Dstdout.encoding=UTF-8 -cp out chat.server.ChatServer 5000 users.txt
