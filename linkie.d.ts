declare namespace Linkie {
    interface Message {
        delete(): void;
        author: User | Member;
        content: string;
        edit(content: string): void;
        editAsEmbed(content: string): void;
        editAsEmbed(title: string, content: string): void;
        mentionsEveryone: boolean;
        isPinned: boolean;
        isTts: boolean;
        channelId: string;
        guildId: string | null;
    }
    
    interface Channel {
        sendEmbed(content: string | ((spec: EmbedSpec) => void)): void;
        sendEmbed(title: string, content: string): void;
        sendMessage(content: string): void;
        id: string;
        mention: string;
    }
    
    interface EmbedSpec {
        title: string;
        description: string;
        url: string;
        color: string;
        image: string;
        thumbnail: string;
    }
    
    interface User {
        username: string;
        discriminator: string;
        discriminatedName: string;
        mention: string;
        id: string;
        isBot: boolean;
        avatarUrl: string;
        animatedAvatar: boolean;
    }
    
    interface Member extends User {
        nickname: string;
        displayName: string;
    }
    
    interface Engine {
        prefix: string;
        cmd: string;
        flags: string[];
        runGlobalTrick(script: string): void;
        runGlobalTrick(script: string, args: string[]): void;
        escapeUrl(url: string): string;
    }
    
    interface System {
        currentTimeMillis(): number;
        nanoTime(): number;
    }
    
    interface Namespaces {
        namespaces: Namespace[]
    }
    
    interface Namespace {
        id: string;
        reloading: boolean;
        defaultVersion: string;
        versions: string[];
        supportsAT: boolean;
        supportsAW: boolean;
        supportsMixin: boolean;
        supportsFieldDescription: boolean;
    }
}

declare let args: string[];
declare let message: Linkie.Message;
declare let channel: Linkie.Channel;
declare let engine: Linkie.Engine;
declare let system: Linkie.System;
declare let namespaces: Linkie.Namespaces;

declare function validateArgsEmpty(): void;
declare function validateArgsNotEmpty(usage: string): void;
