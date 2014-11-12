import java.util.ArrayList;
import java.util.Arrays;

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
		double outSum = 0;
		double inSum = 0;
		int index = 0;
		byte[] hash = tx.getHash();

		UTXOPool currPool = new UTXOPool();
        
            
		ArrayList<Transaction.Output> outputArray = tx.getOutputs();
        for (Transaction.Output out : outputArray) {
			UTXO current = new UTXO(hash, index);
            
            //TODO: Fix this 
            /*
			// Checks condition (1): all outputs claimed by tx are in the current UTXO pool
			if (!pool.contains(current))
				return false;			
            
            
			// Checks condition (3): no UTXO is claimed multiple times by tx, 
			if (currPool.contains(current)) 
				return false;
            */
			// Checks condition (4): all the txns outputs are non-negative
			if (out.value < 0) {
				return false;
			}
			outSum += out.value;

			//currPool.addUTXO(current, out);
			index++;
		}

		// sum the inputs
		ArrayList<Transaction.Input> inputArray = tx.getInputs();
        index = 0;
		for (Transaction.Input in : inputArray) {
			
			// sum inputs
			UTXO toFind = new UTXO(in.prevTxHash, in.outputIndex);
			Transaction.Output found = pool.getTxOutput(toFind);
            
            if (found == null)
                return false;

            if (found.value < 0)
                return false;

			inSum += found.value;

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
            
            index++;

            */
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
			for (Transaction tx: toCheckAgain) {
				count = 0;
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

} 
