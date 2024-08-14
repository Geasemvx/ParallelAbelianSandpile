package parallelAbelianSandpile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.locks.ReentrantLock;

/* Parallel program to simulate an Abelian Sandpile cellular automaton
 * Optimized for performance and scalability
 */

class AutomatonSimulation extends RecursiveAction {
    static final boolean DEBUG = false; // for debugging output

    static long startTime = 0;
    static long endTime = 0;
    private static int CUTOFF; // Variable CUTOFF
    private final int lo;
    private final int hi;
    private static boolean changeDetected = false; 
    static Grid rGrid;
    private static final ReentrantLock lock = new ReentrantLock(); // Lock for deterministic updates

    public AutomatonSimulation(int lo, int hi) {
        this.lo = lo;
        this.hi = hi;
    }

    // timers - note milliseconds
    private static void tick() { // start timing
        startTime = System.currentTimeMillis();
    }

    private static void tock() { // end timing
        endTime = System.currentTimeMillis();
    }

    // input is via a CSV file
    public static int[][] readArrayFromCSV(String filePath) {
        int[][] array = null;
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine();
            if (line != null) {
                String[] dimensions = line.split(",");
                int width = Integer.parseInt(dimensions[0]);
                int height = Integer.parseInt(dimensions[1]);
                System.out.printf("Rows: %d, Columns: %d\n", width, height); // Do NOT CHANGE - you must output this

                array = new int[height][width];
                int rowIndex = 0;

                while ((line = br.readLine()) != null && rowIndex < height) {
                    String[] values = line.split(",");
                    for (int colIndex = 0; colIndex < width; colIndex++) {
                        array[rowIndex][colIndex] = Integer.parseInt(values[colIndex]);
                    }
                    rowIndex++;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return array;
    }

    public static void main(String[] args) throws IOException {

        Grid simulationGrid; // the cellular automaton grid
        if (args.length!=2) {   //input is the name of the input and output files
    		System.out.println("Incorrect number of command line arguments provided.");   	
    		System.exit(0);}

        String inputFileName = args[0]; // input file name
        String outputFileName = args[1]; // output file name

        // Read from input .csv file
        simulationGrid = new Grid(readArrayFromCSV(inputFileName));

        int processors = Runtime.getRuntime().availableProcessors();
        int rows = simulationGrid.getRows();

        CUTOFF = Math.max(10, rows / processors); // for optimal performance

        ForkJoinPool pool = new ForkJoinPool(processors);
        int counter = -1;//so to counteract the spilover
        tick(); // start timer

        do {
            changeDetected = false; // Reset
            pool.invoke(new AutomatonSimulation(1, Grid.rows - 1)); // start parallel computation
            simulationGrid.applyUpdates(); // Apply updates after each iteration
            counter++;
        } while (changeDetected);

        pool.shutdown();

        tock(); // end timer

        System.out.println("Simulation complete, writing image...");
        simulationGrid.gridToImage(outputFileName); // write grid as an image - you must do this.
        // Do NOT CHANGE below!
        // simulation details - you must keep these lines at the end of the output in the parallel versions
        System.out.printf("Number of steps to stable state: %d \n", counter);
        System.out.printf("Time: %d ms\n", endTime - startTime); /*  Total computation time */
    }

    @Override
    protected void compute() {
        if (hi - lo < CUTOFF) {
            lock.lock(); // Lock to ensure deterministic execution
            try {
                for (int i = lo; i < hi; i++) {
                    for (int j = 1; j < Grid.columns - 1; j++) {
                        int grains = Grid.grid[i][j];
                        if (grains >= 4) {
                            Grid.updateGrid[i][j] -= 4;
                            Grid.updateGrid[i - 1][j] += 1;
                            Grid.updateGrid[i + 1][j] += 1; 
                            Grid.updateGrid[i][j - 1] += 1;
                            Grid.updateGrid[i][j + 1] += 1; 
                            changeDetected = true; // a change has taken place
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        } else {
            int mid = (hi + lo) / 2;
            AutomatonSimulation left = new AutomatonSimulation(lo, mid);
            AutomatonSimulation right = new AutomatonSimulation(mid, hi);
            
            left.fork();
            right.compute();
            left.join();
        }
    }

    public static class Grid {
        public static int rows, columns;
        public static int[][] grid; // grid
        public static int[][] updateGrid; // grid for next time step

        public Grid(int w, int h) {
            rows = w + 2; // for the "sink" border
            columns = h + 2; // for the "sink" border
            grid = new int[rows][columns];
            updateGrid = new int[rows][columns];

            /* grid initialization */
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < columns; j++) {
                    grid[i][j] = 0;
                    updateGrid[i][j] = 0;
                }
            }
        }

        public Grid(int[][] newGrid) {
            this(newGrid.length, newGrid[0].length); // call constructor above

            // don't copy over sink border
            for (int i = 1; i < rows - 1; i++) {
                for (int j = 1; j < columns - 1; j++) {
                    grid[i][j] = newGrid[i - 1][j - 1];
                }
            }
        }

        public Grid(Grid copyGrid) {
            this(copyGrid.rows, copyGrid.columns); // call constructor above
            /* grid initialization */
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < columns; j++) {
                    this.grid[i][j] = copyGrid.get(i, j);
                }
            }
        }

        public int getRows() {
            return rows - 2; // less the sink
        }

        public int getColumns() {
            return columns - 2; // less the sink
        }

        public int[][] getGrid() {
            return grid;
        }

        public int[][] getUpdateGrid() {
            return updateGrid;
        }

        int get(int i, int j) {
            return this.grid[i][j];
        }

        void setAll(int value) {
            for (int i = 1; i < rows - 1; i++) {
                for (int j = 1; j < columns - 1; j++)
                    grid[i][j] = value;
            }
        }

        // Efficient parallel update application
        public void applyUpdates() {
            for (int i = 1; i < rows - 1; i++) {
                for (int j = 1; j < columns - 1; j++) {
                    grid[i][j] += updateGrid[i][j];
                    updateGrid[i][j] = 0; // Reset update grid for next iteration
                }
            }
        }

        // display the grid in text format
        void printGrid() {
            int i, j;
            System.out.printf("Grid:\n");
            System.out.printf("+");
            for (j = 1; j < columns - 1; j++) System.out.printf("  --");
            System.out.printf("+\n");
            for (i = 1; i < rows - 1; i++) {
                System.out.printf("|");
                for (j = 1; j < columns - 1; j++) {
                    if (grid[i][j] > 0)
                        System.out.printf("%4d", grid[i][j]);
                    else
                        System.out.printf("    ");
                }
                System.out.printf("|\n");
            }
            System.out.printf("+");
            for (j = 1; j < columns - 1; j++) System.out.printf("  --");
            System.out.printf("+\n\n");
        }

        // write grid out as an image
        void gridToImage(String fileName) throws IOException {
            BufferedImage dstImage =
                    new BufferedImage(rows, columns, BufferedImage.TYPE_INT_ARGB);
            int a = 0;
            int g = 0; // green
            int b = 0; // blue
            int r = 0; // red

            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < columns; j++) {
                    g = 0;
                    b = 0;
                    r = 0;

                    switch (grid[i][j]) {
                        case 0:
                            break;
                        case 1:
                            g = 255;
                            break;
                        case 2:
                            b = 255;
                            break;
                        case 3:
                            r = 255;
                            break;
                        default:
                            break;
                    }
                    int dpixel = (0xff000000)
                            | (a << 24)
                            | (r << 16)
                            | (g << 8)
                            | b;
                    dstImage.setRGB(j, i, dpixel);

                }
            }

            File dstFile = new File(fileName);
            ImageIO.write(dstImage, "png", dstFile);
        }
    }
}
