package com.example.magrathea.storageengine.infrastructure.pipeline;

import java.util.Arrays;

/** Shared systematic GF(256) codec used by bounded EC encoding and reconstruction. */
final class GaloisField256Codec {

    private GaloisField256Codec() {
    }

    static byte[][] encodeParity(byte[][] dataShards, int parityBlocks) {
        validateDataShards(dataShards);
        if (parityBlocks < 1) {
            throw new IllegalArgumentException("parityBlocks must be >= 1: " + parityBlocks);
        }
        byte[][] parity = new byte[parityBlocks][];
        for (int parityIndex = 0; parityIndex < parityBlocks; parityIndex++) {
            parity[parityIndex] = encodeShard(
                    dataShards, dataShards.length + parityIndex, dataShards.length);
        }
        return parity;
    }

    static void reconstructMissingData(
            byte[][] shards,
            int[] selectedShardIndices,
            int dataBlocks,
            int shardSize) {
        if (selectedShardIndices.length != dataBlocks) {
            throw new IllegalArgumentException(
                    "selected shard count must equal dataBlocks: " + selectedShardIndices.length);
        }

        byte[][] selected = new byte[dataBlocks][];
        int[][] matrix = new int[dataBlocks][dataBlocks];
        for (int row = 0; row < dataBlocks; row++) {
            int shardIndex = selectedShardIndices[row];
            byte[] shard = shards[shardIndex];
            if (shard == null || shard.length != shardSize) {
                throw new IllegalArgumentException(
                        "selected shard " + shardIndex + " does not have the committed shard size");
            }
            selected[row] = shard;
            for (int column = 0; column < dataBlocks; column++) {
                matrix[row][column] = generatorCoefficient(shardIndex, column, dataBlocks);
            }
        }

        int[][] inverse = invert(matrix);
        for (int dataIndex = 0; dataIndex < dataBlocks; dataIndex++) {
            if (shards[dataIndex] != null) {
                continue;
            }
            byte[] reconstructed = new byte[shardSize];
            for (int selectedIndex = 0; selectedIndex < dataBlocks; selectedIndex++) {
                int coefficient = inverse[dataIndex][selectedIndex];
                if (coefficient == 0) {
                    continue;
                }
                byte[] input = selected[selectedIndex];
                for (int offset = 0; offset < shardSize; offset++) {
                    reconstructed[offset] ^= (byte) multiply(input[offset] & 0xff, coefficient);
                }
            }
            shards[dataIndex] = reconstructed;
        }
    }

    static byte[] encodeShard(byte[][] dataShards, int shardIndex, int dataBlocks) {
        validateDataShards(dataShards);
        if (dataShards.length != dataBlocks) {
            throw new IllegalArgumentException(
                    "data shard count does not match dataBlocks: " + dataShards.length);
        }
        if (shardIndex < dataBlocks) {
            return Arrays.copyOf(dataShards[shardIndex], dataShards[shardIndex].length);
        }

        int shardSize = dataShards[0].length;
        byte[] encoded = new byte[shardSize];
        for (int dataIndex = 0; dataIndex < dataBlocks; dataIndex++) {
            int coefficient = generatorCoefficient(shardIndex, dataIndex, dataBlocks);
            byte[] input = dataShards[dataIndex];
            for (int offset = 0; offset < shardSize; offset++) {
                encoded[offset] ^= (byte) multiply(input[offset] & 0xff, coefficient);
            }
        }
        return encoded;
    }

    private static int generatorCoefficient(int shardIndex, int dataIndex, int dataBlocks) {
        if (shardIndex < dataBlocks) {
            return shardIndex == dataIndex ? 1 : 0;
        }
        int parityIndex = shardIndex - dataBlocks;
        return power(dataIndex + 1, parityIndex);
    }

    private static int[][] invert(int[][] matrix) {
        int size = matrix.length;
        int[][] augmented = new int[size][size * 2];
        for (int row = 0; row < size; row++) {
            if (matrix[row].length != size) {
                throw new IllegalArgumentException("GF(256) matrix must be square");
            }
            System.arraycopy(matrix[row], 0, augmented[row], 0, size);
            augmented[row][size + row] = 1;
        }

        for (int column = 0; column < size; column++) {
            int pivot = column;
            while (pivot < size && augmented[pivot][column] == 0) {
                pivot++;
            }
            if (pivot == size) {
                throw new IllegalArgumentException("Selected EC survivor matrix is singular");
            }
            if (pivot != column) {
                int[] swap = augmented[pivot];
                augmented[pivot] = augmented[column];
                augmented[column] = swap;
            }

            int pivotInverse = inverse(augmented[column][column]);
            for (int index = 0; index < size * 2; index++) {
                augmented[column][index] = multiply(augmented[column][index], pivotInverse);
            }

            for (int row = 0; row < size; row++) {
                if (row == column) {
                    continue;
                }
                int factor = augmented[row][column];
                if (factor == 0) {
                    continue;
                }
                for (int index = 0; index < size * 2; index++) {
                    augmented[row][index] ^=
                            multiply(factor, augmented[column][index]);
                }
            }
        }

        int[][] inverse = new int[size][size];
        for (int row = 0; row < size; row++) {
            System.arraycopy(augmented[row], size, inverse[row], 0, size);
        }
        return inverse;
    }

    private static int inverse(int value) {
        if (value == 0) {
            throw new IllegalArgumentException("Zero has no GF(256) inverse");
        }
        return power(value, 254);
    }

    private static int power(int value, int exponent) {
        int result = 1;
        for (int i = 0; i < exponent; i++) {
            result = multiply(result, value);
        }
        return result;
    }

    /** Multiplication modulo x^8+x^4+x^3+x^2+1 (0x11d). */
    private static int multiply(int left, int right) {
        int result = 0;
        int a = left;
        int b = right;
        while (b != 0) {
            if ((b & 1) != 0) {
                result ^= a;
            }
            boolean high = (a & 0x80) != 0;
            a = (a << 1) & 0xff;
            if (high) {
                a ^= 0x1d;
            }
            b >>>= 1;
        }
        return result;
    }

    private static void validateDataShards(byte[][] dataShards) {
        if (dataShards == null || dataShards.length == 0 || dataShards[0] == null) {
            throw new IllegalArgumentException("dataShards must contain at least one shard");
        }
        int shardSize = dataShards[0].length;
        for (int index = 0; index < dataShards.length; index++) {
            if (dataShards[index] == null || dataShards[index].length != shardSize) {
                throw new IllegalArgumentException(
                        "data shard " + index + " has inconsistent size");
            }
        }
    }
}
