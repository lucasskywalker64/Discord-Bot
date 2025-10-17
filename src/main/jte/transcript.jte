@import com.github.lucasskywalker64.ticket.model.TranscriptData
@import java.time.format.DateTimeFormatter

@param TranscriptData data

<!DOCTYPE html>
<html lang="en">
<head>
    <title>Transcript for #${data.channelName()}</title>
    <meta charset="UTF-8">
    <meta name="version" content="1.0.1">
    <link rel="icon" type="image/png" href="/icons/transcript-white.png" media="(prefers-color-scheme: dark)">
    <link rel="icon" type="image/png" href="/icons/transcript-black.png" media="(prefers-color-scheme: light)">
    <link rel="stylesheet" href="/css/transcript.css">
</head>
<body data-ticket-id="${data.ticketId()}">
<div class="header">
    <h1>Transcript for #${data.channelName()}</h1>
    <p>${data.messages().size()} messages</p>
</div>

@for(var msg : data.messages())
    <div class="message-group">
        <div class="author-avatar"><img src="${msg.avatarUrl}" alt="Avatar"></div>
        <div class="message-content">
            <div class="message-header">
                <span class="author-name">${msg.authorName}</span>
                <span class="timestamp">
                    <time datetime="${msg.timestampInstant}">
                        ${msg.offsetDateTime.format(DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm a O"))}
                    </time>
                </span>
            </div>

            @if(!msg.content.isEmpty())
                <div class="message-text">$unsafe{msg.content}</div>
            @endif

            @for(var embed : msg.embeds)
                @template.shared.embed(embed = embed)
            @endfor

            @for(var attachment : msg.attachments)
                !{String attachmentKey = String.format("guild-%s/ticket-%s/%s-%s", data.guildId(), data.ticketId(), attachment.id(), attachment.fileName());}
                !{String fileSize;}
                @if(attachment.size() >= 1_048_576)
                    !{fileSize = String.format("%d MB", attachment.size() / 1_048_576);}
                @elseif(attachment.size() >= 1024)
                    !{fileSize = String.format("%d KB", attachment.size() / 1_024);}
                @else
                    !{fileSize = "1 KB";}
                @endif
                <div class="message-attachment">
                    @if(attachment.contentType() != null)
                        @if(attachment.contentType().startsWith("image/"))
                            <img alt="Image Attachment" class="attachment-image" data-attachment-key="${attachmentKey}" src="">
                        @elseif(attachment.contentType().startsWith("video"))
                            <video controls class="attachment-video" data-attachment-key="${attachmentKey}">
                                <source type="${attachment.contentType()}">
                                Video attachment can&apos;t be loaded.
                            </video>
                        @elseif(attachment.contentType().startsWith("audio/"))
                            <div class="discord-audio-player">
                                <div class="player-file-info">
                                    <div class="file-icon audio-file-icon">
                                        <svg viewBox="0 0 102.55 122.88">
                                            <path fill="currentColor" d="M102.55,122.88H0V0h77.66l24.89,26.44L102.55,122.88L102.55,122.88z M36.37,48.54l32.61-7.47l2.45-0.56l0,2.5 v38.51l0.01,0.09l0.01,0.05l0.02,0.2l0.02,0.16l0.02,0.4l0.01,0.09v0.06l-0.01,0.02c-0.01,0.62-0.11,1.25-0.28,1.84 c-0.17,0.59-0.42,1.16-0.72,1.69c-1.47,2.57-4.51,4.69-8,5.29c-0.68,0.12-1.33,0.17-1.95,0.17c-1.88,0-3.59-0.51-4.91-1.39 c-1.44-0.97-2.43-2.38-2.73-4.09h0v-0.01c-0.06-0.35-0.09-0.71-0.09-1.06v-0.06l0.01-0.02c0.01-0.62,0.11-1.25,0.28-1.84 c0.17-0.59,0.42-1.16,0.72-1.69c1.47-2.57,4.51-4.69,8-5.29l0.05-0.01l0.49-0.07l0.11-0.01L62.97,76l0.09-0.01l0.47-0.02l0.05,0 V56.83l-21.38,6.14v26.32l0.01,0.13v0.08l0.01,0.23l0.01,0.05v0.06l-0.01,0.02c-0.01,0.71-0.12,1.42-0.32,2.1 c-0.2,0.66-0.48,1.31-0.83,1.93c-1.71,2.99-5.25,5.46-9.3,6.16c-0.78,0.13-1.54,0.2-2.26,0.2c-2.18,0-4.14-0.58-5.66-1.6 c-1.64-1.1-2.76-2.7-3.1-4.64c-0.07-0.4-0.1-0.8-0.1-1.2v-0.06l0.01-0.02c0.01-0.71,0.12-1.42,0.32-2.1 c0.2-0.66,0.48-1.32,0.83-1.93c1.71-2.99,5.24-5.46,9.3-6.16l0.02,0l0.18-0.03l0.02-0.01c0.21-0.03,0.49-0.07,0.87-0.1 c0.28-0.03,0.57-0.05,0.86-0.05l0.01,0c0.45-0.01,0.89,0.01,1.32,0.04l0-33.86H36.37L36.37,48.54z M96.13,115.98V30.44H73.45V5.91 H6.51v110.07H96.13L96.13,115.98z"/>
                                        </svg>
                                    </div>
                                    <div class="file-details">
                                        <span class="file-name">${attachment.fileName()}</span>
                                        <span class="file-size">${fileSize}</span>
                                    </div>
                                </div>
                                <div class="player-controls">
                                    <button class="play-pause-button" type="button"><svg class="play-pause-icon" width="24" height="24" viewBox="0 0 24 24"></svg></button>
                                    <div class="time-display">0:00 / 0:00</div>
                                    <div class="progress-bar-wrapper"><div class="progress-bar"></div></div>
                                    <div class="volume-control">
                                        <button class="volume-button" type="button"><svg class="volume-icon" width="20" height="20" viewBox="0 0 24 24"></svg></button>
                                        <div class="volume-slider-wrapper"><input type="range" min="0" max="1" step="0.01" value="1" class="volume-slider"></div>
                                    </div>
                                </div>
                            </div>
                            <audio preload="metadata" data-attachment-key="${attachmentKey}" style="display: none;">
                                <source type="${attachment.contentType()}">
                            </audio>
                        @else
                            <div class="attachment-file" data-attachment-key="${attachmentKey}">
                                <div class="file-icon"></div>
                                <div class="file-info">
                                    <span class="file-name">${attachment.fileName()}</span>
                                    <span class="file-size">${fileSize}</span>
                                </div>
                            </div>
                        @endif
                    @endif
                </div>
            @endfor
        </div>
    </div>
@endfor

<script src="/js/transcript.js"></script>
</body>
</html>