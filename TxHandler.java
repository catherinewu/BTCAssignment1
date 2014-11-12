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
		double outSum = 0.0;
		double inSum = 0.0;
		int index = 0;
		byte[] hash = tx.getHash();

		//UTXOPool currPool = new UTXOPool();
        ArrayList<UTXO> usedUTXO = new ArrayList<UTXO>();

        /* Check the inputs to the transaction for validity */
		ArrayList<Transaction.Input> inputArray = tx.getInputs();
		for (Transaction.Input in : inputArray) {

			UTXO current = new UTXO(in.prevTxHash, in.outputIndex);

			// Checks condition (1): all outputs claimed by tx are in the current UTXO pool
			if (!pool.contains(current))
				return false;			
           
            if (usedUTXO.contains(current))
                return false;

            usedUTXO.add(current);

			// sum inputs
			Transaction.Output found = pool.getTxOutput(current);
            
            if (found == null)
                return false;

            if (found.value < 0)
                return false;

			inSum += found.value;
            
            // Check that the transaction is signed properly 
            byte[] msg = tx.getRawDataToSign(index);
            if (msg == null)
                return false;

            byte[] sig = in.signature;
            if (sig == null)
                return false;

            if (!found.address.verifySignature(msg, sig))
                return false;

            /*

			// Checks condition (2): the signatures on each input of tx are valid
			//get the message and the signature and then verify it
            
            // TODO: this is a stopgap for figuring out why
            // we keep getting out of bounds errors
            //if (in.outputIndex > inputArray.size())
            //    return false;
			byte[] msg = tx.getRawDataToSign(index);

            if (msg == null)
                return false;

            byte[] sig = in.signature;
			if (!found.address.verifySignature(msg, sig)) {
				return false;
			}
            */

            index++;

		}

		ArrayList<Transaction.Output> outputArray = tx.getOutputs();
        for (Transaction.Output out : outputArray) {
			// Checks condition (4): all the txns outputs are non-negative
			if (out.value < 0) {
				return false;
			}
			outSum += out.value;

			index++;
		}

		// Checks condition (5): the sum of tx’s input values is 
		// greater than or equal to the sum of its output values
		if (! (inSum >= outSum)) 
			return false;

		return true;
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
			pool.removeUTXO(toAdd);
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
        public static HashMap<UTXO, HashSet<Transaction>> constructUTXOMapping(Transaction[] txs, UTXOPool pool) {

            for (Transaction tx : txs) {

            }

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

} 
