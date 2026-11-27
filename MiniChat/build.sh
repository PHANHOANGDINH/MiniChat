#!/bin/sh
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out src/chat/common/*.java src/chat/server/*.java src/chat/client/*.java && echo "Biên dịch thành công"
