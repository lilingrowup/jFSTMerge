package br.ufpe.cin.mergers.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import br.ufpe.cin.exceptions.TextualMergeException;
import br.ufpe.cin.files.FilesManager;
import br.ufpe.cin.mergers.TextualMerge;
import br.ufpe.cin.mergers.util.MergeContext;
import de.ovgu.cide.fstgen.ast.FSTNode;
import de.ovgu.cide.fstgen.ast.FSTTerminal;

/**
 * Semistructured merge uses elements identifier to match nodes, if a node has no identifier, 
 * the algorithm is unable to match this node. This lead to problems with initialization block 
 * as these elements has no identifier, semistructured merge cannot match them, and therefore creates duplicates.
 * To avoid this problem, we attempt to match them by textual similarity.
 * @author Guilherme
 */
public class InitializationBlocksHandler {

	public static void handle(MergeContext context,	List<FSTNode> leftInitlBlocks, List<FSTNode> baseInitlBlocks, List<FSTNode> rightInitlBlocks) throws TextualMergeException {
		List<Triple> matchedInitlBlocks = new ArrayList<Triple>();

		//1. when there is only a single block, this is the match
		if(leftInitlBlocks.size()==1 && rightInitlBlocks.size()==1 && baseInitlBlocks.size()==1){
			Triple matched 	   = new Triple(leftInitlBlocks.get(0), baseInitlBlocks.get(0), rightInitlBlocks.get(0));
			matchedInitlBlocks.add(matched);
		} else {
			//2. find similar left and right nodes to a base node
			for(FSTNode baseBlock : baseInitlBlocks){
				//search similar node in left and remove from the list of left nodes
				FSTNode leftSimilarBlock = leftInitlBlocks.stream()                        
						.filter(leftBlock -> areSimilarBlocks(baseBlock, leftBlock))       
						.findFirst()                                      
						.orElse(null);
				leftInitlBlocks = leftInitlBlocks.stream()
						.filter(leftBlock -> !leftSimilarBlock.equals(leftBlock))
						.collect(Collectors.toList());

				//search similar node in right and remove from the list of right nodes
				FSTNode rightSimilarBlock= rightInitlBlocks.stream()
						.filter(rightBlock -> areSimilarBlocks(baseBlock, rightBlock))
						.findFirst()
						.orElse(null);
				rightInitlBlocks = rightInitlBlocks.stream()
						.filter(rightBlock -> !rightSimilarBlock.equals(rightBlock))
						.collect(Collectors.toList());

				//only when there similar (left and right) nodes we proceed
				if(rightSimilarBlock != null && leftSimilarBlock != null){ 
					Triple matched 	   = new Triple(leftSimilarBlock, baseBlock, rightSimilarBlock);
					matchedInitlBlocks.add(matched);
				}
			}

			//3. in cases there is no base similar, but still left and right might be similar
			for(FSTNode leftBlock : leftInitlBlocks){
				//search similar node in right and remove from the list of right nodes
				FSTNode rightSimilarBlock= rightInitlBlocks.stream()
						.filter(rightBlock -> areSimilarBlocks(leftBlock, rightBlock))
						.findFirst()
						.orElse(null);
				rightInitlBlocks = rightInitlBlocks.stream()
						.filter(rightBlock -> !rightSimilarBlock.equals(rightBlock))
						.collect(Collectors.toList());

				if(rightSimilarBlock  != null){
					Triple matched 	   = new Triple(leftBlock, null, rightSimilarBlock);
					matchedInitlBlocks.add(matched);
				}
			}
		}

		//4. merge the matched triples
		for(Triple tp : matchedInitlBlocks){
			String leftcontent = (tp.left!=null)?((FSTTerminal) tp.left).getBody() : "";
			String basecontent = (tp.base!=null)?((FSTTerminal) tp.base).getBody() : "";
			String rightcontent=(tp.right!=null)?((FSTTerminal) tp.right).getBody(): "";

			String mergedContent = TextualMerge.merge(leftcontent, basecontent, rightcontent, true);

			//5. updating merged AST
			if(tp.left != null && tp.right != null){
				FilesManager.findAndReplaceASTNodeContent(context.superImposedTree, leftcontent , mergedContent);
				FilesManager.findAndDeleteASTNode(context.superImposedTree, rightcontent);
			} else if(tp.left == null){
				FilesManager.findAndReplaceASTNodeContent(context.superImposedTree, rightcontent , mergedContent);
			} else if(tp.right == null){
				FilesManager.findAndReplaceASTNodeContent(context.superImposedTree, leftcontent , mergedContent);
			}

			//statistics
			if(mergedContent.contains("<<<<<<<")) //has conflict
				context.initializationBlocksConflicts++;
		}
	}

	/**
	 * Verifies if the content of two given <i>Initialization Blocks</i> are similar.
	 * @param first initialization block
	 * @param second initialization block
	 * @return <b>true</b> if the contents are similar, <b>false</b> if not
	 */
	private static boolean areSimilarBlocks(FSTNode first, FSTNode second) {
		String firstContent = ((FSTTerminal)first).getBody();
		String secondContent= ((FSTTerminal)second).getBody();
		double similarity   = FilesManager.computeStringSimilarity(firstContent, secondContent);
		if(similarity > 0.70){ 	//are similar
			return true;
		} else { 				//are different
			return false;
		}
	}
}

/**
 * Innerclass representing triple of nodes. 
 * @author Guilherme
 */
class Triple {
	FSTNode left;
	FSTNode base;
	FSTNode right;

	public Triple(FSTNode left, FSTNode base, FSTNode right){
		this.left = left;
		this.base = base;
		this.right= right;
	}
}
