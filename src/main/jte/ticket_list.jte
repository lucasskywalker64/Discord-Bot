@import com.github.lucasskywalker64.ticket.model.Ticket
@import java.time.format.DateTimeFormatter
@import java.util.List
@import net.dv8tion.jda.api.entities.Guild
@import io.github.cdimascio.dotenv.Dotenv

@param List<Ticket> ticketList
@param Guild guild
@param Dotenv config

<!DOCTYPE html>
<html lang="en">
<style>
    tr:nth-child(even) {
        background-color: #D6EEEE;
    }
    td {
        padding: 5px;
    }
</style>
<head>
    <title>Your Tickets</title>
    <meta charset="UTF-8">
</head>
<body>
<table>
    <tr>
        <th>Created At</th>
        <th>Claimed By</th>
        <th>Closed At</th>
        <th>Closed By</th>
        <th>Reason</th>
        <th>Transcript</th>
    </tr>
    @for(var ticket : ticketList)
        <tr>
            <td style="white-space: nowrap;">
                <time datetime="${ticket.createdAt().toInstant().toString()}">
                    ${ticket.createdAt().toOffsetDateTime().format(DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm a O"))}
                </time>
            </td>
            @if(ticket.claimerId() != 0)
                <td style="white-space: nowrap;">
                    ${guild.retrieveMemberById(ticket.claimerId()).complete().getEffectiveName()}
                </td>
            @else
                <td></td>
            @endif
            @if(ticket.isClosed())
                <td style="white-space: nowrap">
                    <time datetime="${ticket.closedAt().toInstant().toString()}">
                        ${ticket.closedAt().toOffsetDateTime().format(DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm a O"))}
                    </time>
                </td>
                <td style="white-space: nowrap;">
                    ${guild.retrieveMemberById(ticket.closerId()).complete().getEffectiveName()}
                </td>
                <td>
                    ${ticket.reason()}
                </td>
                <td style="white-space: nowrap;">
                    !{String transcriptListUrl;}
                    @if("prod".equals(System.getProperty("app.env", "prod")))
                        !{transcriptListUrl = String.format("%s/tickets/transcripts/%s", config.get("SERVER_BASE_URL"), ticket.id());}
                    @else
                        !{transcriptListUrl = String.format("%s:%s/tickets/transcripts/%s", config.get("SERVER_BASE_URL"), config.get("SERVER_PORT"), ticket.id());}
                    @endif
                    <a href="${transcriptListUrl}" target="_blank">link</a>
                </td>
            @else
                <td></td>
                <td></td>
                <td></td>
                <td></td>
            @endif
        </tr>
    @endfor
</table>
</body>
<script>
    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('time').forEach(timeElement => {
            const dateString = timeElement.getAttribute('datetime');
            if (dateString) {
                const localDate = new Date(dateString);
                const options = { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit', timeZoneName: 'short' };
                timeElement.textContent = localDate.toLocaleString(undefined, options);
            }
        });
    })
</script>
</html>