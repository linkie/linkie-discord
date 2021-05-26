validateArgsEmpty()
channel.sendEmbed("List of Namespaces", namespaces.namespaces.map(value => value.id).join(", "))
