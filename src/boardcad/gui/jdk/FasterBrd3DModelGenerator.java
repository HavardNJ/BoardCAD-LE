package boardcad.gui.jdk;

import java.util.Vector;

import org.jogamp.java3d.Shape3D;
import org.jogamp.java3d.IndexedQuadArray;
import org.jogamp.java3d.QuadArray;
import org.jogamp.vecmath.*;

import board.BezierBoard;



public class FasterBrd3DModelGenerator {
	boolean mCancelExecuting = false;
	Vector<Thread> mThreads = new Vector<Thread>();
	boolean mInitialModelRun = true;

	public void update3DModel(BezierBoard brd, Shape3D model, int numTasks, boolean forceRefresh) {
		mCancelExecuting = true;
		//System.out.println("BezierBoard.update3DModel() cancel execution, waiting for threads");
		for (Thread thread : mThreads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				System.out.println("BezierBoard.update3DModel() InterruptedException");
			}
		}
		mThreads.clear();

		//System.out.println("BezierBoard.update3DModel() Done waiting ");

		if(forceRefresh){
			model.removeAllGeometries();
			mInitialModelRun = true;
		}

		if (brd.isEmpty())
			return;

		if (model.numGeometries() != numTasks) {
			//System.out.printf("BezierBoard.update3DModel() Need initial run geom: %d tasks: %d\n", model.numGeometries(), numTasks);

			model.removeAllGeometries();
			mInitialModelRun = true;
		} else {
			mInitialModelRun = false;
		}

		mCancelExecuting = false;
		double length = brd.getLength();

		for (int i = 0; i < numTasks; i++) {
			final double sx = (length / numTasks) * i;
			final double ex = (length / numTasks) * (i + 1);
			final int index = i;
			Runnable task = () -> {
				update3DModel((BezierBoard) brd.clone(), model, sx, ex, index);
			};

			Thread thread = new Thread(task);
			mThreads.add(thread);
			thread.start();
		}

	}

	public void update3DModel(BezierBoard brd, Shape3D model, double startX, double endX, int index) {
		double lengthAccuracy = 1.0;
		double widthMinAccuracy = 1.0;

		double spanLength = endX - startX;
		double width = brd.getCenterWidth();

		boolean isTail = startX <= 0.0;
		boolean isNose = endX >= brd.getLength();

		int lengthSteps = (int) (spanLength / lengthAccuracy) + 1;
		int widthSteps = (int) ((width / 2.0) / widthMinAccuracy) + 1;

		double lengthStep = spanLength / lengthSteps;

		int nrOfCoords = lengthSteps * (widthSteps * 2) * 4 * 2;
		if(isTail || isNose){
			nrOfCoords += (widthSteps * 2) * 4 * 2;
		}

		QuadArray quads = new QuadArray(nrOfCoords, IndexedQuadArray.COORDINATES
				| IndexedQuadArray.NORMALS);

		Point3d[][] deckVertices = new Point3d[widthSteps+1][lengthSteps+1];
		Vector3f[][] deckNormals = new Vector3f[widthSteps+1][lengthSteps+1];
		Point3d[] quadCoords = new Point3d[lengthSteps*4];
		Vector3f[] quadNormals = new Vector3f[lengthSteps*4];

		int nrOfQuads = 0;
		double xPos = 0.0;

		// Deck
		double minAngle = -45.0;
		double maxAngle = 150.0;

		//Generate deck coordinates
		for (int i = 0; i <= widthSteps; i++) {
			if (mCancelExecuting)
				return;

			xPos = startX;
			for (int j = 0; j <= lengthSteps; j++) {

				deckVertices[i][j] = new Point3d(brd.getSurfacePoint(xPos, minAngle, maxAngle, i, widthSteps));
				deckNormals[i][j] = new Vector3f(brd.getSurfaceNormal(xPos, minAngle, maxAngle,i, widthSteps));
				if(i == 0){
					deckVertices[i][j].setY(0.0);
				}
				xPos += lengthStep;
			}
		}

		//Generate quads
		for (int i = 0; i < widthSteps; i++) {
			if (mCancelExecuting)
				return;

			int q = 0;
			for (int j = 0; j < lengthSteps; j++) {
				quadCoords[q] = deckVertices[i][j];
				quadNormals[q] = deckNormals[i][j];
				++q;
				quadCoords[q] = deckVertices[i][j+1];
				quadNormals[q] = deckNormals[i][j+1];
				++q;
				quadCoords[q] = deckVertices[i+1][j+1];
				quadNormals[q] = deckNormals[i+1][j+1];
				++q;
				quadCoords[q] = deckVertices[i+1][j];
				quadNormals[q] = deckNormals[i+1][j];
				++q;
			}
			quads.setCoordinates(nrOfQuads * 4, quadCoords);
			quads.setNormals(nrOfQuads * 4, quadNormals);
			nrOfQuads += lengthSteps;
		}

		//Mirror deck coordinates
		for (int i = 0; i <= widthSteps; i++) {
			if (mCancelExecuting)
				return;

			for (int j = 0; j <= lengthSteps; j++) {
				deckVertices[i][j].setY(-deckVertices[i][j].getY());
				deckNormals[i][j].setY(-deckNormals[i][j].getY());
			}
		}

		//Generate mirrored quads
		for (int i = 0; i < widthSteps; i++) {
			if (mCancelExecuting)
				return;

			int q = 0;
			for (int j = 0; j < lengthSteps; j++) {
				quadCoords[q] = deckVertices[i+1][j];
				quadNormals[q] = deckNormals[i+1][j];
				++q;
				quadCoords[q] = deckVertices[i+1][j+1];
				quadNormals[q] = deckNormals[i+1][j+1];
				++q;
				quadCoords[q] = deckVertices[i][j+1];
				quadNormals[q] = deckNormals[i][j+1];
				++q;
				quadCoords[q] = deckVertices[i][j];
				quadNormals[q] = deckNormals[i][j];
				++q;
			}
			quads.setCoordinates(nrOfQuads * 4, quadCoords);
			quads.setNormals(nrOfQuads * 4, quadNormals);
			nrOfQuads += lengthSteps;
		}

		//Generate bottom
		minAngle = maxAngle;
		maxAngle = 360.0;

		Point3d[][] bottomVertices = new Point3d[widthSteps+1][lengthSteps+1];
		Vector3f[][] bottomNormals = new Vector3f[widthSteps+1][lengthSteps+1];

		for (int i = 0; i <= widthSteps; i++) {
			if (mCancelExecuting)
				return;

			xPos = startX;

			for (int j = 0; j <= lengthSteps; j++) {
				bottomVertices[i][j] = brd.getSurfacePoint(xPos, minAngle, maxAngle, i, widthSteps);
				bottomNormals[i][j] = new Vector3f(brd.getSurfaceNormal(xPos, minAngle, maxAngle,i, widthSteps));
				if(i == widthSteps){
					bottomVertices[i][j].setY(0.0);
				}
				xPos += lengthStep;
			}
		}

		//Generate quads
		for (int i = 0; i < widthSteps; i++) {
			if (mCancelExecuting)
				return;

			int q = 0;
			for (int j = 0; j < lengthSteps; j++) {
				quadCoords[q] = bottomVertices[i][j];
				quadNormals[q] = bottomNormals[i][j];
				++q;
				quadCoords[q] = bottomVertices[i][j+1];
				quadNormals[q] = bottomNormals[i][j+1];
				++q;
				quadCoords[q] = bottomVertices[i+1][j+1];
				quadNormals[q] = bottomNormals[i+1][j+1];
				++q;
				quadCoords[q] = bottomVertices[i+1][j];
				quadNormals[q] = bottomNormals[i+1][j];
				++q;
			}
			quads.setCoordinates(nrOfQuads * 4, quadCoords);
			quads.setNormals(nrOfQuads * 4, quadNormals);
			nrOfQuads += lengthSteps;
		}

		//Mirror bottom coordinates
		for (int i = 0; i <= widthSteps; i++) {
			if (mCancelExecuting)
				return;

			for (int j = 0; j <= lengthSteps; j++) {
				bottomVertices[i][j].setY(-bottomVertices[i][j].getY());
				bottomNormals[i][j].setY(-bottomNormals[i][j].getY());
			}
		}

		//Generate mirrored quads
		for (int i = 0; i < widthSteps; i++) {
			if (mCancelExecuting)
				return;

			int q = 0;
			for (int j = 0; j < lengthSteps; j++) {
				quadCoords[q] = bottomVertices[i+1][j];
				quadNormals[q] = bottomNormals[i+1][j];
				++q;
				quadCoords[q] = bottomVertices[i+1][j+1];
				quadNormals[q] = bottomNormals[i+1][j+1];
				++q;
				quadCoords[q] = bottomVertices[i][j+1];
				quadNormals[q] = bottomNormals[i][j+1];
				++q;
				quadCoords[q] = bottomVertices[i][j];
				quadNormals[q] = bottomNormals[i][j];
				++q;
			}
			quads.setCoordinates(nrOfQuads * 4, quadCoords);
			quads.setNormals(nrOfQuads * 4, quadNormals);
			nrOfQuads += lengthSteps;
		}

		//Create tail patch
		if(isTail){
			int q = 0;
			quadCoords = new Point3d[widthSteps*4];
			quadNormals = new Vector3f[widthSteps*4];

			for (int i = 0; i < widthSteps; i++) {
				quadCoords[q] = bottomVertices[i+1][0];
				quadNormals[q] = bottomNormals[i+1][0];
				++q;
				quadCoords[q] = bottomVertices[i][0];
				quadNormals[q] = bottomNormals[i][0];
				++q;
				quadCoords[q] = deckVertices[(widthSteps - i)][0];
				quadNormals[q] = deckNormals[(widthSteps - i)][0];
				++q;
				quadCoords[q] = deckVertices[(widthSteps - i)-1][0];
				quadNormals[q] = deckNormals[(widthSteps - i)-1][0];
				++q;
			}
			quads.setCoordinates(nrOfQuads * 4, quadCoords);
			quads.setNormals(nrOfQuads * 4, quadNormals);
			nrOfQuads += q/4;

			//Mirror
			for (int i = 0; i <= widthSteps; i++) {
				bottomVertices[i][0].setY(-bottomVertices[i][0].getY());
				deckVertices[i][0].setY(-deckVertices[i][0].getY());
			}
			q = 0;
			for (int i = 0; i < widthSteps; i++) {
				quadCoords[q] = bottomVertices[i][0];
				quadNormals[q] = bottomNormals[i][0];
				++q;
				quadCoords[q] = bottomVertices[i+1][0];
				quadNormals[q] = bottomNormals[i+1][0];
				++q;
				quadCoords[q] = deckVertices[(widthSteps - i)-1][0];
				quadNormals[q] = deckNormals[(widthSteps - i)-1][0];
				++q;
				quadCoords[q] = deckVertices[(widthSteps - i)][0];
				quadNormals[q] = deckNormals[(widthSteps - i)][0];
				++q;
			}
			quads.setCoordinates(nrOfQuads * 4, quadCoords);
			quads.setNormals(nrOfQuads * 4, quadNormals);
			nrOfQuads += q/4;
		}

		//Create nose patch
		if(isNose){
			int q = 0;
			int steps = widthSteps;
			quadCoords = new Point3d[steps*4];
			quadNormals = new Vector3f[steps*4];

			for (int i = 0; i < steps; i++) {
				quadCoords[q] = bottomVertices[i][lengthSteps];
				quadNormals[q] = bottomNormals[i][lengthSteps];
				++q;
				quadCoords[q] = bottomVertices[i+1][lengthSteps];
				quadNormals[q] = bottomNormals[i+1][lengthSteps];
				++q;
				quadCoords[q] = deckVertices[(widthSteps - i)-1][lengthSteps];
				quadNormals[q] = deckNormals[(widthSteps - i)-1][lengthSteps];
				++q;
				quadCoords[q] = deckVertices[(widthSteps - i)][lengthSteps];
				quadNormals[q] = deckNormals[(widthSteps - i)][lengthSteps];
				++q;
			}
			quads.setCoordinates(nrOfQuads * 4, quadCoords);
			quads.setNormals(nrOfQuads * 4, quadNormals);
			nrOfQuads += q/4;

			//Mirror
			for (int i = 0; i <= widthSteps; i++) {
				bottomVertices[i][lengthSteps].setY(-bottomVertices[i][lengthSteps].getY());
				deckVertices[i][lengthSteps].setY(-deckVertices[i][lengthSteps].getY());
			}
			q = 0;
			for (int i = 0; i < steps; i++) {
				quadCoords[q] = bottomVertices[i+1][lengthSteps];
				quadNormals[q] = bottomNormals[i+1][lengthSteps];
				++q;
				quadCoords[q] = bottomVertices[i][lengthSteps];
				quadNormals[q] = bottomNormals[i][lengthSteps];
				++q;
				quadCoords[q] = deckVertices[(widthSteps - i)][lengthSteps];
				quadNormals[q] = deckNormals[(widthSteps - i)][lengthSteps];
				++q;
				quadCoords[q] = deckVertices[(widthSteps - i)-1][lengthSteps];
				quadNormals[q] = deckNormals[(widthSteps - i)-1][lengthSteps];
				++q;
			}
			quads.setCoordinates(nrOfQuads * 4, quadCoords);
			quads.setNormals(nrOfQuads * 4, quadNormals);
			nrOfQuads += q/4;
		}

		try {
			if (mInitialModelRun) {
				model.addGeometry(quads);
			} else {
				model.setGeometry(quads, index);
			}
		} catch (Exception e) {
			System.out.printf("BezierBoard.update3DModel() model.setGeometry() failed, index: %d numGeometries: %d\n", index, model.numGeometries());
			e.printStackTrace(System.out);
		}

	}
}
