package com.indeavour.coreader.repository

import android.util.Log
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.flatMap
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

class ReaderRepository(
    private val context: Context
) {

    private val httpClient = DefaultHttpClient()

    private val assetRetriever = AssetRetriever(
        contentResolver = context.contentResolver,
        httpClient = httpClient
    )

    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = PdfiumDocumentFactory(context)
        )
    )

    suspend fun openBook(file: File): Result<Publication> {
        Log.d("ReaderRepository", "openBook called for: ${file.absolutePath}")
        return withContext(Dispatchers.IO) {
            assetRetriever.retrieve(file)
                .flatMap { asset ->
                    Log.d("ReaderRepository", "Asset retrieved: $asset")
                    publicationOpener.open(asset, allowUserInteraction = true)
                }
                .toResult()
                .also {
                    Log.d("ReaderRepository", "Open result: $it")
                }
        }
    }
}

private fun <T> org.readium.r2.shared.util.Try<T, *>.toResult(): Result<T> =
    fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(Exception(it.toString())) }
    )
