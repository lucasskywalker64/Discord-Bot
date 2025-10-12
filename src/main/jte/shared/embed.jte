@import com.github.lucasskywalker64.ticket.model.TranscriptEmbed
@import com.github.lucasskywalker64.ticket.model.TranscriptEmbed.EmbedField

@param TranscriptEmbed embed

<div class="embed">
    <div class="embed-sidebar" style="background-color: ${embed.color() != null ? embed.color() : null}"></div>

    <div class="embed-content">
        @if (embed.author() != null)
            <div class="embed-author">
                @if (embed.author().iconUrl() != null)
                    <img src="${embed.author().iconUrl()}" alt="Author Icon" class="embed-author-icon">
                @endif
                <span class="embed-author-name">${embed.author().name()}</span>
            </div>
        @endif

        @if (embed.title() != null)
            <div class="embed-title">
                @if (embed.url() != null)
                    <a href="${embed.url()}">${embed.title()}</a>
                @else
                    ${embed.title()}
                @endif
            </div>
        @endif

        @if (embed.description() != null)
            <div class="embed-description">${embed.description()}</div>
        @endif

        @if (!embed.fields().isEmpty())
            <div class="embed-fields">
                @for(EmbedField field : embed.fields())
                    <div class="embed-field${field.isInline() ? ".full-width" : ""}">
                        <div class="embed-field-name">${field.name()}</div>
                        <div class="embed-field-value">${field.value()}</div>
                    </div>
                @endfor
            </div>
        @endif

        @if (embed.image() != null)
            <div class="embed-image">
                <img src="${embed.image().url()}" alt="Embed Image">
            </div>
        @endif

        @if(embed.footer() != null)
            <div class="embed-footer">
                <span class="embed-footer-text">${embed.footer().text()}</span>
            </div>
        @endif
    </div>
</div>