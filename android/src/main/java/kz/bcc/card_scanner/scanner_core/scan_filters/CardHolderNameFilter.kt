package kz.bcc.card_scanner.scanner_core.scan_filters

import com.google.mlkit.vision.text.Text
import kz.bcc.card_scanner.logger.debugLog
import kz.bcc.card_scanner.scanner_core.constants.CardHolderNameConstants
import kz.bcc.card_scanner.scanner_core.constants.CardScannerRegexps
import kz.bcc.card_scanner.scanner_core.models.CardHolderNameScanPositions
import kz.bcc.card_scanner.scanner_core.models.CardHolderNameScanResult
import kz.bcc.card_scanner.scanner_core.models.CardNumberScanResult
import kz.bcc.card_scanner.scanner_core.models.CardScannerOptions
import kz.bcc.card_scanner.scanner_core.models.ScanFilter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class CardHolderNameFilter(
    visionText: Text,
    scannerOptions: CardScannerOptions,
    private val cardNumberScanResult: CardNumberScanResult
) : ScanFilter(visionText, scannerOptions) {
    private val cardHolderRegex: Regex =
        Regex(CardScannerRegexps.cardHolderName, RegexOption.MULTILINE)
    private val maxBlocksBelowCardNumberToSearchForName = 4

    override fun filter(): CardHolderNameScanResult? {
        if (!scannerOptions.scanCardHolderName) return null
        if (cardNumberScanResult.cardNumber.isEmpty()) return null

        ///Search from card number block and below [_maxBlocksBelowCardNumberToSearchForName] blocks
        val minTextBlockIndexToSearchName = max(
            cardNumberScanResult.textBlockIndex -
                    (if (scannerOptions.possibleCardHolderNamePositions?.contains(
                            CardHolderNameScanPositions.aboveCardNumber.value
                        ) == true
                    ) 1 else 0), 0
        )
        val maxTextBlockIndexToSearchName =
            min(
                cardNumberScanResult.textBlockIndex +
                        (if (scannerOptions.possibleCardHolderNamePositions?.contains((CardHolderNameScanPositions.belowCardNumber.value)) == true) maxBlocksBelowCardNumberToSearchForName else 0),
                visionText.textBlocks.size - 1
            )

        for (index in minTextBlockIndexToSearchName..maxTextBlockIndexToSearchName) {
            val block = visionText.textBlocks[index]
            val transformedBlockText = transformBlockText(block.text)
            if (!cardHolderRegex.containsMatchIn(transformedBlockText)) continue;
            val cardHolderName = cardHolderRegex.find(transformedBlockText)!!.value.trim()
            if (isValidName(cardHolderName)) return CardHolderNameScanResult(
                textBlockIndex = index,
                textBlock = block,
                cardHolderName = cardHolderName,
                visionText = visionText
            )
        }
        return null;
    }

    private fun isValidName(cardHolder: String): Boolean {
        if (cardHolder.length < 3 || cardHolder.length > scannerOptions.maxCardHolderNameLength) {
            debugLog(
                "maxCardHolderName length = " + scannerOptions.maxCardHolderNameLength,
                scannerOptions
            )
            return false
        }
        if (cardHolder.startsWith("valid from") || cardHolder.startsWith("valid thru")) return false;
        if (cardHolder.endsWith("valid from") || cardHolder.endsWith("valid thru")) return false;
        return !CardHolderNameConstants.defaultBlackListedWords
            .union(scannerOptions.cardHolderNameBlackListedWords?.toSet() ?: emptySet())
            .contains(cardHolder.lowercase(Locale.ENGLISH))
    }

    private fun transformBlockText(blockText: String): String {
        return blockText.replace('c', 'C')
            .replace('o', 'O')
            .replace('p', 'P')
            .replace('v', 'V')
            .replace('w', 'W')
    }
}