/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.node.parser;

import bisq.core.dao.node.parser.exceptions.InvalidGenesisTxException;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.OpReturnType;
import bisq.core.dao.state.blockchain.RawTx;
import bisq.core.dao.state.blockchain.TempTx;
import bisq.core.dao.state.blockchain.TempTxOutput;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxInput;
import bisq.core.dao.state.blockchain.TxOutputKey;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.governance.Param;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Verifies if a given transaction is a BSQ transaction.
 */
@Slf4j
public class TxParser {
    private final PeriodService periodService;
    private final BsqStateService bsqStateService;
    private TxOutputParser txOutputParser;
    private TxInputParser txInputParser;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TxParser(PeriodService periodService,
                    BsqStateService bsqStateService) {
        this.periodService = periodService;
        this.bsqStateService = bsqStateService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Apply state changes to tx, inputs and outputs
    // return Tx if any input contained BSQ
    // Any tx with BSQ input is a BSQ tx (except genesis tx but that is not handled in
    // that class).
    // There might be txs without any valid BSQ txOutput but we still keep track of it,
    // for instance to calculate the total burned BSQ.
    public Optional<Tx> findTx(RawTx rawTx, String genesisTxId, int genesisBlockHeight, Coin genesisTotalSupply) {
        txInputParser = new TxInputParser(bsqStateService);
        txOutputParser = new TxOutputParser(bsqStateService);

        // Let's see if we have a genesis tx
        Optional<TempTx> optionalGenesisTx = TxParser.findGenesisTx(
                genesisTxId,
                genesisBlockHeight,
                genesisTotalSupply,
                rawTx);
        if (optionalGenesisTx.isPresent()) {
            TempTx genesisTx = optionalGenesisTx.get();
            txOutputParser.processGenesisTxOutput(genesisTx);
            return Optional.of(Tx.fromTempTx(genesisTx));
        }

        // If it is not a genesis tx we continue to parse to see if it is a valid BSQ tx.
        int blockHeight = rawTx.getBlockHeight();
        // We could pass tx also to the sub validators but as long we have not refactored the validators to pure
        // functions lets use the parsingModel.
        TempTx tempTx = TempTx.fromRawTx(rawTx);

        for (int inputIndex = 0; inputIndex < tempTx.getTxInputs().size(); inputIndex++) {
            TxInput input = tempTx.getTxInputs().get(inputIndex);
            TxOutputKey outputKey = input.getConnectedTxOutputKey();
            txInputParser.process(outputKey, blockHeight, rawTx.getId(), inputIndex);
        }

        long accumulatedInputValue = txInputParser.getAccumulatedInputValue();
        txOutputParser.setAvailableInputValue(accumulatedInputValue);

        // We don't allow multiple opReturn outputs (they are non-standard but to be safe lets check it)
        long numOpReturnOutputs = tempTx.getTempTxOutputs().stream().filter(txOutputParser::isOpReturnOutput).count();
        if (numOpReturnOutputs > 1) {
            tempTx.setTxType(TxType.INVALID);
            String msg = "Invalid tx. We have multiple opReturn outputs. tx=" + tempTx;
            log.warn(msg);
        }

        txOutputParser.setUnlockBlockHeight(txInputParser.getUnlockBlockHeight());
        txOutputParser.setOptionalSpentLockupTxOutput(txInputParser.getOptionalSpentLockupTxOutput());
        txOutputParser.setTempTx(tempTx); //TODO remove

        boolean hasBsqInputs = accumulatedInputValue > 0;
        if (hasBsqInputs) {
            List<TempTxOutput> outputs = tempTx.getTempTxOutputs();
            // We start with last output as that might be an OP_RETURN output and gives us the specific tx type, so it is
            // easier and cleaner at parsing the other outputs to detect which kind of tx we deal with.
            // Setting the opReturn type here does not mean it will be a valid BSQ tx as the checks are only partial and
            // BSQ inputs are not verified yet.
            // We keep the temporary opReturn type in the parsingModel object.
            checkArgument(!outputs.isEmpty(), "outputs cannot be empty");
            int lastIndex = outputs.size() - 1;
            int lastNonOpReturnIndex = lastIndex;
            if (txOutputParser.isOpReturnOutput(outputs.get(lastIndex))) {
                txOutputParser.processOpReturnOutput(outputs.get(lastIndex));
                lastNonOpReturnIndex -= 1;
            }

            // We use order of output index. An output is a BSQ utxo as long there is enough input value
            // We iterate all outputs including the opReturn to do a full validation including the BSQ fee
            for (int index = 0; index <= lastNonOpReturnIndex; index++) {
                txOutputParser.processTxOutput(outputs.get(index));
            }

            long remainingInputValue = txOutputParser.getAvailableInputValue();
            boolean hasBurntBSQ = remainingInputValue > 0;

            // Apply txType and optional txOutputTypes based on opReturn types
            // We might get a INVALID TxType here
            verifyTxAndOutputs(blockHeight, tempTx, remainingInputValue);

            if (isTxInvalid(tempTx, txOutputParser.isBsqOutputFound()))
                tempTx.setTxType(TxType.INVALID);

            // We might get a INVALID TxType here as well

            TxType txType = evaluateTxType(tempTx, txOutputParser.getOptionalOpReturnType(), hasBurntBSQ,
                    txInputParser.isUnLockInputValid());
            tempTx.setTxType(txType);


            if (tempTx.getTxType() != TxType.INVALID) {
                txOutputParser.commitTxOutputsForValidTx();
            } else {
                txOutputParser.commitTxOutputsForInvalidTx();

                if (hasBurntBSQ) {
                    log.warn("We have destroyed BSQ because of an invalid tx. Burned BSQ={}. tx={}",
                            remainingInputValue / 100D, tempTx);
                }
            }

            //TODO use setBurntFee but check in bsqStateService if txType is INVALID
            if (hasBurntBSQ)
                tempTx.setBurntFee(remainingInputValue);
        }

        // TODO || parsingModel.getBurntBondValue() > 0; should not be necessary
        // How should we consider the burnt BSQ from spending a LOCKUP tx with the wrong format.
        // Example: LOCKUP txOutput is 1000 satoshi but first txOutput in spending tx is 900
        // satoshi, this burns the 1000 satoshi and is currently not considered in the
        // bsqInputBalancePositive, hence the need to check for parsingModel.getBurntBondValue
        // Perhaps adding boolean parsingModel.isBSQTx and checking for that would be better?

        if (hasBsqInputs || txInputParser.getBurntBondValue() > 0)
            return Optional.of(Tx.fromTempTx(tempTx));
        else
            return Optional.empty();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO That method is not testable and still too complex.

    /**
     * This method verifies after all outputs are parsed if the opReturn type and the optional txOutputs required for
     * certain use cases are valid.
     * It verifies also if the fee is correct (if required) and if the phase is correct (if relevant).
     * We set the txType as well as the txOutputType of the relevant outputs.
     */
    private void verifyTxAndOutputs(int blockHeight, TempTx tempTx, long bsqFee) {
        OpReturnType opReturnType = null;
        Optional<OpReturnType> optionalOpReturnType = txOutputParser.getOptionalOpReturnType();
        if (optionalOpReturnType.isPresent()) {
            opReturnType = optionalOpReturnType.get();

            switch (opReturnType) {
                case PROPOSAL:
                    processProposal(blockHeight, tempTx, bsqFee);
                    break;
                case COMPENSATION_REQUEST:
                    processCompensationRequest(blockHeight, tempTx, bsqFee);
                    break;
                case BLIND_VOTE:
                    processBlindVote(blockHeight, tempTx, bsqFee);
                    break;
                case VOTE_REVEAL:
                    processVoteReveal(blockHeight, tempTx);
                    break;
                case LOCKUP:
                    // do nothing
                    break;
            }
        }

        // We need to check if any tempTxOutput is available and if so and the OpReturn data is invalid we
        // set the output to a BTC output. We must not use `if else` cases here!
        if (opReturnType != OpReturnType.COMPENSATION_REQUEST) {
            txOutputParser.getOptionalIssuanceCandidate().ifPresent(tempTxOutput -> tempTxOutput.setTxOutputType(TxOutputType.BTC_OUTPUT));
        }

        if (opReturnType != OpReturnType.BLIND_VOTE) {
            txOutputParser.getOptionalBlindVoteLockStakeOutput().ifPresent(tempTxOutput -> tempTxOutput.setTxOutputType(TxOutputType.BTC_OUTPUT));
        }

        if (opReturnType != OpReturnType.VOTE_REVEAL) {
            txOutputParser.getOptionalVoteRevealUnlockStakeOutput().ifPresent(tempTxOutput -> tempTxOutput.setTxOutputType(TxOutputType.BTC_OUTPUT));
        }

        if (opReturnType != OpReturnType.LOCKUP) {
            txOutputParser.getOptionalLockupOutput().ifPresent(tempTxOutput -> tempTxOutput.setTxOutputType(TxOutputType.BTC_OUTPUT));
        }
    }

    private void processProposal(int blockHeight, TempTx tempTx, long bsqFee) {
        boolean isFeeAndPhaseValid = isFeeAndPhaseValid(blockHeight, bsqFee, DaoPhase.Phase.PROPOSAL, Param.PROPOSAL_FEE);
        if (!isFeeAndPhaseValid) {
            tempTx.setTxType(TxType.INVALID);
        }
    }

    private void processCompensationRequest(int blockHeight, TempTx tempTx, long bsqFee) {
        boolean isFeeAndPhaseValid = isFeeAndPhaseValid(blockHeight, bsqFee, DaoPhase.Phase.PROPOSAL, Param.PROPOSAL_FEE);
        Optional<TempTxOutput> optionalIssuanceCandidate = txOutputParser.getOptionalIssuanceCandidate();
        if (isFeeAndPhaseValid) {
            if (optionalIssuanceCandidate.isPresent()) {
                // Now after we have validated the fee and phase we will apply the TxOutputType
                optionalIssuanceCandidate.get().setTxOutputType(TxOutputType.ISSUANCE_CANDIDATE_OUTPUT);
            } else {
                log.warn("It can be that we have a opReturn which is correct from its structure but the whole tx " +
                        "in not valid as the issuanceCandidate in not there. " +
                        "As the BSQ fee is set it must be either a buggy tx or an manually crafted invalid tx.");
                tempTx.setTxType(TxType.INVALID);
            }
        } else {
            tempTx.setTxType(TxType.INVALID);
            optionalIssuanceCandidate.ifPresent(tempTxOutput -> tempTxOutput.setTxOutputType(TxOutputType.BTC_OUTPUT));
            // Empty Optional case is a possible valid case where a random tx matches our opReturn rules but it is not a
            // valid BSQ tx.
        }
    }

    private void processBlindVote(int blockHeight, TempTx tempTx, long bsqFee) {
        boolean isFeeAndPhaseValid = isFeeAndPhaseValid(blockHeight, bsqFee, DaoPhase.Phase.BLIND_VOTE, Param.BLIND_VOTE_FEE);
        if (!isFeeAndPhaseValid) {
            tempTx.setTxType(TxType.INVALID);
            txOutputParser.getOptionalBlindVoteLockStakeOutput().ifPresent(tempTxOutput -> tempTxOutput.setTxOutputType(TxOutputType.BTC_OUTPUT));
            // Empty Optional case is a possible valid case where a random tx matches our opReturn rules but it is not a
            // valid BSQ tx.
        }
    }

    private void processVoteReveal(int blockHeight, TempTx tempTx) {
        boolean isPhaseValid = isPhaseValid(blockHeight, DaoPhase.Phase.VOTE_REVEAL);
        if (!isPhaseValid) {
            tempTx.setTxType(TxType.INVALID);
        }

        // We must not use an `if else` here!
        if (!isPhaseValid || !txInputParser.isVoteRevealInputValid()) {
            txOutputParser.getOptionalVoteRevealUnlockStakeOutput().ifPresent(
                    tempTxOutput -> tempTxOutput.setTxOutputType(TxOutputType.BTC_OUTPUT));
            // Empty Optional case is a possible valid case where a random tx matches our opReturn rules but it is not a
            // valid BSQ tx.
        }
    }


    /**
     * Whether the BSQ fee and phase is valid for a transaction.
     *
     * @param blockHeight The height of the block that the transaction is in.
     * @param bsqFee      The fee in BSQ, in satoshi.
     * @param phase       The current phase of the DAO, e.g {@code DaoPhase.Phase.PROPOSAL}.
     * @param param       The parameter for the fee, e.g {@code Param.PROPOSAL_FEE}.
     * @return True if the fee and phase was valid, false otherwise.
     */
    private boolean isFeeAndPhaseValid(int blockHeight, long bsqFee, DaoPhase.Phase phase, Param param) {
        // The leftover BSQ balance from the inputs is the BSQ fee in case we are in an OP_RETURN output

        if (!isPhaseValid(blockHeight, phase)) {
            return false;
        }

        long paramValue = bsqStateService.getParamValue(param, blockHeight);
        boolean isFeeCorrect = bsqFee == paramValue;
        if (!isFeeCorrect) {
            log.warn("Invalid fee. used fee={}, required fee={}", bsqFee, paramValue);
        }
        return isFeeCorrect;
    }

    private boolean isPhaseValid(int blockHeight, DaoPhase.Phase phase) {
        boolean isInPhase = periodService.isInPhase(blockHeight, phase);
        if (!isInPhase) {
            log.warn("Not in {} phase. blockHeight={}", phase, blockHeight);
        }
        return isInPhase;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    /**
     * Performs various checks for an invalid tx
     */
    static boolean isTxInvalid(TempTx tempTx, boolean bsqOutputFound) {
        if (tempTx.getTxType() == TxType.INVALID) {
            // We got already set the invalid type in earlier checks and return early.
            return true;
        }

        if (!bsqOutputFound) {
            log.warn("Invalid Tx: No BSQ output found. tx=" + tempTx);
            return true;
        }

        boolean isAnyTxOutputTypeUndefined = tempTx.getTempTxOutputs().stream()
                .anyMatch(txOutput -> TxOutputType.UNDEFINED_OUTPUT == txOutput.getTxOutputType());
        if (isAnyTxOutputTypeUndefined) {
            log.warn("Invalid Tx: We have undefined txOutput types. tx=" + tempTx);
            return true;
        }

        return false;
    }

    /**
     * Retrieve the type of the transaction, assuming it is relevant to bisq.
     *
     * @param tempTx               The temporary transaction.
     * @param optionalOpReturnType The optional OP_RETURN type of the transaction.
     * @param hasBurntBSQ          If the have been remaining value from the inputs which got not spent in outputs.
     *                             Might be valid BSQ fees or burned BSQ from an invalid tx.
     * @return The type of the transaction, if it is relevant to bisq.
     */
    @VisibleForTesting
    static TxType evaluateTxType(TempTx tempTx, Optional<OpReturnType> optionalOpReturnType,
                                 boolean hasBurntBSQ, boolean isUnLockInputValid) {
        if (optionalOpReturnType.isPresent()) {
            // We use the opReturnType to find the txType
            return evaluateTxTypeFromOpReturnType(tempTx, optionalOpReturnType.get());
        }

        // No opReturnType, so we check for the remaining possible cases
        if (hasBurntBSQ) {
            // PAY_TRADE_FEE tx has a fee and no opReturn
            return TxType.PAY_TRADE_FEE;
        }

        // UNLOCK tx has no fee, no opReturn but an UNLOCK_OUTPUT at first output.
        if (tempTx.getTempTxOutputs().get(0).getTxOutputType() == TxOutputType.UNLOCK_OUTPUT) {
            // We check if there have been invalid inputs
            if (!isUnLockInputValid)
                return TxType.INVALID;

            // UNLOCK tx has no fee, no OpReturn
            return TxType.UNLOCK;
        }

        // TRANSFER_BSQ has no fee, no opReturn and no UNLOCK_OUTPUT at first output
        log.debug("No burned fee and no OP_RETURN, so this is a TRANSFER_BSQ tx.");
        return TxType.TRANSFER_BSQ;
    }

    @VisibleForTesting
    static TxType evaluateTxTypeFromOpReturnType(TempTx tempTx, OpReturnType opReturnType) {
        switch (opReturnType) {
            case PROPOSAL:
                return TxType.PROPOSAL;
            case COMPENSATION_REQUEST:
                boolean hasCorrectNumOutputs = tempTx.getTempTxOutputs().size() >= 3;
                if (!hasCorrectNumOutputs) {
                    log.warn("Compensation request tx need to have at least 3 outputs");
                    return TxType.INVALID;
                }

                TempTxOutput issuanceTxOutput = tempTx.getTempTxOutputs().get(1);
                boolean hasIssuanceOutput = issuanceTxOutput.getTxOutputType() == TxOutputType.ISSUANCE_CANDIDATE_OUTPUT;
                if (!hasIssuanceOutput) {
                    log.warn("Compensation request txOutput type of output at index 1 need to be ISSUANCE_CANDIDATE_OUTPUT. " +
                            "TxOutputType={}", issuanceTxOutput.getTxOutputType());
                    return TxType.INVALID;
                }

                return TxType.COMPENSATION_REQUEST;
            case BLIND_VOTE:
                return TxType.BLIND_VOTE;
            case VOTE_REVEAL:
                return TxType.VOTE_REVEAL;
            case LOCKUP:
                return TxType.LOCKUP;
            default:
                log.warn("We got a BSQ tx with an unknown OP_RETURN. tx={}, opReturnType={}", tempTx, opReturnType);
                return TxType.INVALID;
        }
    }


    /**
     * Parse and return the genesis transaction for bisq, if applicable.
     *
     * @param genesisTxId        The transaction id of the bisq genesis transaction.
     * @param genesisBlockHeight The block height of the bisq genesis transaction.
     * @param genesisTotalSupply The total supply of the genesis issuance for bisq.
     * @param rawTx              The candidate transaction.
     * @return The genesis transaction if applicable, or Optional.empty() otherwise.
     */
    @VisibleForTesting
    static Optional<TempTx> findGenesisTx(String genesisTxId, int genesisBlockHeight, Coin genesisTotalSupply,
                                          RawTx rawTx) {
        boolean isGenesis = rawTx.getBlockHeight() == genesisBlockHeight &&
                rawTx.getId().equals(genesisTxId);
        if (!isGenesis)
            return Optional.empty();

        TempTx tempTx = TempTx.fromRawTx(rawTx);
        tempTx.setTxType(TxType.GENESIS);
        long remainingInputValue = genesisTotalSupply.getValue();
        for (int i = 0; i < tempTx.getTempTxOutputs().size(); ++i) {
            TempTxOutput txOutput = tempTx.getTempTxOutputs().get(i);
            long value = txOutput.getValue();
            boolean isValid = value <= remainingInputValue;
            if (!isValid)
                throw new InvalidGenesisTxException("Genesis tx is invalid; using more than available inputs. " +
                        "Remaining input value is " + remainingInputValue + " sat; tx info: " + tempTx.toString());

            remainingInputValue -= value;
            txOutput.setTxOutputType(TxOutputType.GENESIS_OUTPUT);
        }
        if (remainingInputValue > 0) {
            throw new InvalidGenesisTxException("Genesis tx is invalid; not using all available inputs. " +
                    "Remaining input value is " + remainingInputValue + " sat, tx info: " + tempTx.toString());
        }
        return Optional.of(tempTx);
    }
}
