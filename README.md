# Papyrus Bot for Minecraft Bedrock Edition

This is a bot that will join your Minecraft Bedrock Edition server, and serve up chat messages and player positions on a WebSocket for [PapyrusCS](https://github.com/mjungnickel18/papyruscs).

Your server **must** have `online-mode=false` and `allow-cheats=true`.

## Launching

Create a systemd service (or run the Docker command from the command line) somewhere, setting the `MINECRAFT_SERVER_HOST` and optionally `MINECRAFT_SERVER_PORT` addresses:

```
[Unit]
Description=Minecraft Bot

[Service]
ExecStart=/usr/bin/docker run --rm --name=papyrus -p 80:8080 -e MINECRAFT_SERVER_HOST=your.minecraft.server hachque/papyrus-bot
Restart=always

[Install]
WantedBy=multi-user.service
```

Then `systemctl enable minecraft-bot.service && systemctl start minecraft-bot.service`.

When the bot joins the server, you must change set permission level to **operator**. You must do this whenever the bot reconnects, as it uses a unique user ID every time.

## Usage

The command above will serve up a WebSocket on port 80. There's no Papyrus map software that uses this endpoint... yet.

## Features

This bot has the following features:

- Monitors chat messages
- Periodically checks player positions in the Overworld
- Correctly detects when people are in the Nether or The End (and doesn't try to show their position, due to Bedrock limitations)
- Automatically goes to sleep so that players can still skip time overnight

## Demo

You can see one of these WebSockets in action by connecting to `ws://june-mc-websocket.redpoint.games` with [Simple WebSocket Client](https://chrome.google.com/webstore/detail/simple-websocket-client/pfdhoblngboilpfeibdedpjgfnlcodoo/related?hl=en). Depending if there are people on the server, you'll see something like this:

![A screenshot](https://raw.githubusercontent.com/hach-que/papyrus-bot/master/demo.png)

## License

This software uses LGPL licensed libraries, in particular https://github.com/NukkitX/Protocol.
