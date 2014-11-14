import java.util.*;

public class TxHandler {
	public UTXOPool pool;
	/* Creates a public ledger whose current UTXOPool (collection of unspent 
	 * transaction outputs) is utxoPool. This should make a defensive copy of 
	 * utxoPool by using the UTXOPool(UTXOPool uPool) constructor.
	 */
	public TxHandler(UTXOPool utxoPool) {
		pool = new UTXOPool(utxoPool);
	}

    	/* Returns true if
	 * (1) all outputs claimed by tx are in the current UTXO pool,
	 * (2) the signatures on each input of tx are valid,
	 * (3) no UTXO is claimed multiple times by tx,
	 * (4) all of tx’s output values are non-negative, and
	 * (5) the sum of tx’s input values is greater than or equal to the sum of
	        its output values;
	   and false otherwise.
	 */

    public boolean isValidTx(Transaction tx) {
        boolean hasValidInputs = hasValidInputs(tx);
        boolean hasValidOutputs = hasValidOutputs(tx);

        double fee = calculateFee(tx);
        boolean hasValidFee = fee >= 0;

        return hasValidInputs && hasValidOutputs && hasValidFee;
    }

    /* returns true iff
     * (1) claimed outputs are in UTXO pool
     * (2) signatures on each input is valid
     * (3) claimed outputs are unique
     */
    private boolean hasValidInputs(Transaction tx) {
        UTXOPool consumed = new UTXOPool();

        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);

            // test claimed outputs exist and are unique
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            if (!pool.contains(utxo) || consumed.contains(utxo)) {
                return false;
            }
            consumed.addUTXO(utxo, null);

            // test signature validity on this input
            byte[] msg = tx.getRawDataToSign(i);
            byte[] sig = input.signature;
            if (!pool.getTxOutput(utxo).address.verifySignature(msg, sig)) {
                return false;
            }
        }

        return true;
    }

    /* returns true iff (4) all output values are non-negative
     */
    private boolean hasValidOutputs(Transaction tx) {
        for (Transaction.Output output : tx.getOutputs()) {
            if (output.value < 0) return false;
        }
        return true;
    }

    private double calculateFee(Transaction tx) {
        double in = 0;
        for (Transaction.Input input : tx.getInputs()) {
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            in = in + pool.getTxOutput(utxo).value;
        }

        double out = 0;
        for (Transaction.Output output : tx.getOutputs()) {
            out = out + output.value;
        }

        return out - in;
    }

    private double calculateTotalFees(Set<Transaction> txs) {
        double total = 0;

        for (Transaction tx : txs) {
            total += calculateFee(tx);
        }

        return total;
    }



	private void updateUTXO(Transaction tx) {
		byte[] hash = tx.getHash();

		// remove all inputs
		ArrayList<Transaction.Input> inputArray = tx.getInputs();
		for (Transaction.Input in : inputArray) {
			UTXO toRemove = new UTXO(in.prevTxHash, in.outputIndex);
			pool.removeUTXO(toRemove);
		}

		// add all outputs
		ArrayList<Transaction.Output> outputArray = tx.getOutputs();
		int i = 0;
		for (Transaction.Output out : outputArray) {
			UTXO toAdd = new UTXO(hash, i);
			pool.addUTXO(toAdd, out);
			i++;
		}	
	}

	/* Handles each epoch by receiving an unordered array of proposed 
	 * transactions, checking each transaction for correctness, 
	 * returning a mutually valid array of accepted transactions, 
	 * and updating the current UTXO pool as appropriate.
	 */
	public Transaction[] handleTxs(Transaction[] possibleTxs) {
		TxHandler handle = new TxHandler(pool);
		ArrayList<Transaction> acceptedTx = new ArrayList<Transaction>();
		ArrayList<Transaction> toCheckAgain = new ArrayList<Transaction>();
		int count = 1;
		for (Transaction tx : possibleTxs) {

			// check is transaction is valid
			if (!handle.isValidTx(tx)) {
				toCheckAgain.add(tx);
				continue;
			}

			updateUTXO(tx);
			acceptedTx.add(tx);
		}


		// while one thing has been added to acceptedTx in last round
		while (count >= 0) {
            count = 0;
			for (Transaction tx: toCheckAgain) {
				if(!handle.isValidTx(tx)) {
					continue;
				}
				count++;
				updateUTXO(tx);
				acceptedTx.add(tx);
				toCheckAgain.remove(tx);
			}
		}


		// change to array
		Transaction[] acceptedArr = new Transaction[acceptedTx.size()];
		acceptedArr = acceptedTx.toArray(acceptedArr);

		return acceptedArr;
	}

    public static class TxHandlerUtil {
        public static HashMap<UTXO, Transaction> constructUTXOMapping(Transaction[] txs, UTXOPool pool) {
            HashMap<UTXO, Transaction> map = new HashMap<UTXO, Transaction>();

            for (UTXO utxo : pool.getAllUTXO()) {
                map.put(utxo, null);
            }

            for (Transaction tx : txs) {
                for (int i = 0; i < tx.getOutputs().size(); i++) {
                    UTXO utxo = new UTXO(tx.getHash(), i);
                    map.put(utxo, tx);
                }
            }

            return map;
        }

        public static HashMap<Transaction, HashSet<Transaction>> constructTxDependencies(Transaction[] txs, UTXOPool pool) {
            HashMap<UTXO, Transaction> txForUtxo = constructUTXOMapping(txs, pool);
            HashMap<Transaction, HashSet<Transaction>> dep = new HashMap<Transaction, HashSet<Transaction>>();

            for (Transaction tx : txs) {
                if (!dep.containsKey(tx)) {
                    dep.put(tx, new HashSet<Transaction>());
                }

                for (Transaction.Input input : tx.getInputs()) {
                    UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                    // this is an invalid tx because it consumes a utxo that doesn't exist
                    if (!txForUtxo.containsKey(utxo)) {
                        dep.remove(tx);
                        break;
                    }

                    dep.get(tx).add(txForUtxo.get(utxo));
                }
            }

            return dep;
        }


        public static HashSet<HashSet<Transaction>> calculateMaximalSets(Transaction[] txs) {
            HashMap<Transaction, HashSet<Transaction>> conflicts = findConflicts(txs);
            return getMaximalDisjointSets(conflicts);
        }

        // use the Bron-Kerbosch algorithm (http://en.wikipedia.org/wiki/Bron%E2%80%93Kerbosch_algorithm)
        // to find all the maximal independent sets
        public static void bronKerbosch(Set<Transaction> taken, Set<Transaction> remaining, Set<Transaction> excluded,
                                        Set<HashSet<Transaction>> ret, Map<Transaction, HashSet<Transaction>> neighbors) {
            if (remaining.isEmpty() && excluded.isEmpty()) {
                ret.add(new HashSet<Transaction>(taken));
                return;
            }

            Set<Transaction> r = new HashSet<Transaction>(taken);
            Set<Transaction> p = new HashSet<Transaction>(remaining);
            Set<Transaction> x = new HashSet<Transaction>(excluded);

            for (Transaction v : remaining) {
                Set<Transaction> n = neighbors.get(v);

                // R union {v}
                Set<Transaction> rNew = new HashSet<Transaction>(r);
                rNew.add(v);

                // P intersect neigbors(v)
                Set<Transaction> pNew = new HashSet<Transaction>(p);
                p.retainAll(n);

                // X intersect neighbors(v)
                Set<Transaction> xNew = new HashSet<Transaction>(x);
                x.retainAll(n);

                bronKerbosch(rNew, pNew, xNew, ret, neighbors);

                p.remove(v);
                x.remove(v);
            }
        }

        // calculate the maximal disjoint sets using the Bron-Kerbosch algorithm
        public static HashSet<HashSet<Transaction>> getMaximalDisjointSets(HashMap<Transaction, HashSet<Transaction>> conflicts) {
            Set<Transaction> vertices = conflicts.keySet();

            HashSet<HashSet<Transaction>> ret = new HashSet<HashSet<Transaction>>();
            bronKerbosch(new HashSet<Transaction>(), vertices, new HashSet<Transaction>(), ret, conflicts);

            return ret;
        }

        // construct the conflict graph
        public static HashMap<Transaction, HashSet<Transaction>> findConflicts(Transaction[] txs) {
            HashMap<Transaction.Input, HashSet<Transaction>> consumes = new HashMap<Transaction.Input, HashSet<Transaction>>();
            for (Transaction tx : txs) {
                for (Transaction.Input input : tx.getInputs()) {
                    if (!consumes.containsKey(input)) consumes.put(input, new HashSet<Transaction>());
                    consumes.get(input).add(tx);
                }
            }

            HashMap<Transaction, HashSet<Transaction>> conflicts = new HashMap<Transaction, HashSet<Transaction>>();
            for (HashSet<Transaction> conflicting : consumes.values()) {
                if (conflicting.size() > 1) {
                    for (Transaction tx1 : conflicting) {
                        for (Transaction tx2 : conflicting) {
                            if (!tx1.equals(tx2)) {
                                // tx1 conflicts with tx2
                                if (!conflicts.containsKey(tx1)) conflicts.put(tx1, new HashSet<Transaction>());
                                conflicts.get(tx1).add(tx2);
                            }
                        }
                    }
                }
            }

            return conflicts;
        }
    }

    // find if the txSet is valid
    public boolean checkIfValid(HashSet<Transaction> txSet) {
        UTXOPool poolCopy = new UTXOPool(pool);
        UTXOPool newUTXO = new UTXOPool();
        for (Transaction tx : txSet) {
            if (!hasValidOutputs(tx)) {
                return false;
            }

            byte[] hash = tx.getHash();
            ArrayList<Transaction.Output> outputArray = tx.getOutputs();
            int i = 0;
            for (Transaction.Output out : outputArray) {
                UTXO toAdd = new UTXO(hash, i);
                newUTXO.addUTXO(toAdd, out);
                i++;
            }
        }

        for (Transaction tx : txSet) {
            for (int i = 0; i < tx.numInputs(); i++) {
                Transaction.Input input = tx.getInput(i);
                UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                boolean pool = poolCopy.contains(utxo); 
                boolean inNew = newUTXO.contains(utxo);
                if (!pool && !inNew) {
                    return false;
                }
                else if (pool) {
                    poolCopy.removeUTXO(utxo);
                }
                else {
                    newUTXO.removeUTXO(utxo);
                }
            }
        }
        return true;
    }
} 
