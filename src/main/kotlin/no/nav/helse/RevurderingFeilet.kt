package no.nav.helse

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.Revurderingsperiode.Companion.somRevurderinger
import java.util.*
import javax.sql.DataSource

class RevurderingFeilet(rapidApplication: RapidsConnection, private val dataSource: () -> DataSource): River.PacketListener {
    init {
        River(rapidApplication).apply {
            precondition {
                it.requireValue("@event_name", "vedtaksperiode_endret")
                it.requireValue("gjeldendeTilstand", "REVURDERING_FEILET")
            }
            validate {
                it.require("vedtaksperiodeId") { id -> UUID.fromString(id.asText()) }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val vedtaksperiodeId = packet["vedtaksperiodeId"].let { UUID.fromString(it.asText()) }

        dataSource().transactional {
            val uferdigeRevurderinger = Revurderingsperiode.alleUferdigeRevurderinger(listOf(vedtaksperiodeId), this)
                .map { it.feilet(vedtaksperiodeId) }
                .somRevurderinger()

            uferdigeRevurderinger.forEach {
                it.lagreStatus(this, context)
            }
        }
    }
}
