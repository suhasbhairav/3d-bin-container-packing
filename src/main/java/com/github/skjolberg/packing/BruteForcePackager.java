package com.github.skjolberg.packing;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.iterators.PermutationIterator;

/**
 * Fit boxes into container, i.e. perform bin packing to a single container. 
 * 
 * This attempts a brute force approach, which is very demanding in terms of resources. Only use for scenarios with
 * few boxes.
 * 
 * Thread-safe implementation.
 */

public class BruteForcePackager extends Packager {

	public BruteForcePackager(List<? extends Dimension> containers, boolean rotate3d, boolean footprintFirst) {
		super(containers, rotate3d, footprintFirst);
	}

	public BruteForcePackager(List<? extends Dimension> containers) {
		super(containers);
	}

	@Override
	protected Container pack(List<Box> boxes, Dimension containerBox, long timeout) {
		for(Box box : boxes) {
			if(rotate3D) {
				if(!box.canFitInside3D(containerBox)) {
					return null;
				}
			} else {
				if(!box.canFitInside2D(containerBox)) {
					return null;
				}
			}
		}
		
		Container holder = new Container(containerBox);

		PermutationIterator<Box> iterator = new PermutationIterator<Box>(boxes);
		while(iterator.hasNext()) {
			if(System.currentTimeMillis() > timeout) {
				break;
			}

			List<Box> permutation = iterator.next();
			
			/*
			System.out.println("Permutations");
			
			StringBuffer permutationNames = new StringBuffer();
			for(int k = 0; k < permutation.size(); k++) {
				permutationNames.append(permutation.get(k).getName());
				permutationNames.append(" ");
			}
			System.out.println(permutationNames);
*/
			int[] rotations = new int[permutation.size()];
			
			permutation:
			while(true) {
				/*
				StringBuffer buffer = new StringBuffer();
				for(int k = 0; k < rotations.length; k++) {
					buffer.append(rotations[k]);
					buffer.append(" ");
				}

				buffer.append(" ");
				for(Box box : permutation) {
					buffer.append(box.getWidth() + "x" + box.getDepth() + "x" + box.getHeight());
					buffer.append(" ");
				}

				System.out.println(" " + buffer);
				 */
				if(System.currentTimeMillis() > timeout) {
					break;
				}
				fit: {
					// check sanity of current rotation
					for(Box box : permutation) {
						if(!box.fitsInside3D(containerBox)) {
							break fit;
						}
					}
		
					holder.clear();
					
					List<Box> containerProducts = new ArrayList<Box>(permutation);

					while(!containerProducts.isEmpty()) {
						Box box = containerProducts.remove(0);
						
						Dimension space = holder.getRemainigFreeSpace();
	
						if(!box.fitsInside3D(space)) {
							break fit;
						}
						
						Space levelSpace = new Space(
									containerBox.getWidth(), 
									containerBox.getDepth(), 
									box.getHeight(), 
									0, 
									0, 
									holder.getStackHeight()
									);
						
						holder.addLevel();
			
						fit2D(containerProducts, holder, box, levelSpace);
					}
					
					//break fit;
					return holder;			
				}
			
				do {
					// next rotation
					int rotateBoxIndex = rotate(rotations);
					if(rotateBoxIndex == -1) {
						// done
						break permutation;
					}
					
					Box box = permutation.get(rotateBoxIndex);
					if(rotations[rotateBoxIndex] % 2 == 0) {
						if(box.isSquare2D()) {
							// skip 2d rotation
							rotations[rotateBoxIndex]++;
							
							continue;
						}
						box.rotate2D();
					} else {
						if(box.isSquare3D()) {
							// skip 2d and 3d rotations
							rotations[rotateBoxIndex] = 6; 
							
							continue;
						}
						if(box.isSquare2D()) {
							box.rotate3D();
						} else {
							box.rotate2D3D();
						}
					}
					break;
				} while(true);
			}
		}
		return null;
	}	
	
	protected int rotate(int[] rotations) {
		// next rotation
		for(int i = 0; i < rotations.length; i++) {
			if(rotations[i] < (rotate3D ? 6 : 2)) {
				rotations[i]++;
				
				// reset all previous counter to zero
				for(int k = 0; k < i; k++) {
					rotations[k] = 0;
				}
				
				return i;
			}
		}		
		
		return -1;
	}
	
	protected void fit2D(List<Box> containerProducts, Container holder, Box usedSpace, Space freeSpace) {

		// add used space box now
		// there is up to possible 2 free spaces
		holder.add(new Placement(freeSpace, usedSpace));

		if(containerProducts.isEmpty()) {
			return;
		}
		
		Space[] spaces = getFreespaces(freeSpace, usedSpace, false);

		Placement nextPlacement = firstSpacePlacement(containerProducts.get(0), spaces);
		if(nextPlacement == null) {
			// no additional boxes
			// just make sure the used space fits in the free space
			return;
		}
		
		// holder.validateCurrentLevel(); // uncomment for debugging
		
		containerProducts.remove(0);

		// attempt to fit in the remaining (usually smaller) space first
		
		// stack in the 'sibling' space - the space left over between the used box and the selected free space
		Space remainder = nextPlacement.getSpace().getRemainder();
		if(!remainder.isEmpty()) {
			
			Box box = containerProducts.get(0);
			
			if(box.fitsInside3D(remainder)) {
				containerProducts.remove(0);
				
				fit2D(containerProducts, holder, box, remainder);
			}
		}

		// fit the next box in the selected free space
		fit2D(containerProducts, holder, nextPlacement.getBox(), nextPlacement.getSpace());
	}	

	protected Placement firstSpacePlacement(Box box, Space[] spaces) {
		for(Space space : spaces) {
			if(space != null) {
				if(box.fitsInside3D(space)) {
					return new Placement(space, box);
				}
			}
		}
		return null;
	}	
	
}
