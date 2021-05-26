validateArgsNotEmpty("<term>")
channel.sendMessage("https://letmegooglethat.com/?q=" + engine.escapeUrl(args.join(" ")))