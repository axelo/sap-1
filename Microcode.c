#include <errno.h>
#include <getopt.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

// 16 available control signals.
enum Signal {
    ZERO_BUS_OUT = 0,
    RAM_BUS_OUT = 1,
    PC_BUS_OUT = 2,
    IR_BUS_OUT = 3,
    REGA_BUS_OUT = 4,
    REGB_BUS_OUT = 5,
    ALU_BUS_OUT = 6, // 0..2 bits bus out address.
    RAM_BUS_IN = 1 << 3,
    MAR_BUS_IN = 2 << 3,
    PC_BUS_IN = 3 << 3,
    IR_BUS_IN = 4 << 3,
    REGA_BUS_IN = 5 << 3,
    REGB_BUS_IN = 6 << 3,
    OUT_BUS_IN = 7 << 3, // 3..5 bits bus in address.
    HALT = 1 << 6,
    PC_COUNT = 1 << 7,
    ALU_SUBTRACT = 1 << 8,
    OUT_SIGNED_IN = 1 << 9,
    FLAGS_IN = 1 << 10,
    // 1 << 10 Unused.
    // 1 << 11 Unused.
    // 1 << 12 Unused.
    // 1 << 13 Unused.
    // 1 << 14 Unused.
    LAST_STEP = 1 << 15 // Last step bit.
};

enum {
    MAX_NB_OF_CONTROL_STEPS = 8, // First 3 bits of microcode address.
    NB_OF_INSTRUCTIONS = 16,     // Next 4 bits of microcode address.
    NB_OF_FLAG_PERMUTATIONS = 4  // Next 2 bits of microcode address.
};

#define FETCH_STEP0 PC_BUS_OUT | MAR_BUS_IN
#define FETCH_STEP1 RAM_BUS_OUT | IR_BUS_IN | PC_COUNT

#define ONLY_FETCH_STEPS \
    { FETCH_STEP0,       \
      FETCH_STEP1 | LAST_STEP }

#define WITH_FETCH_STEPS(...) \
    { FETCH_STEP0,            \
      FETCH_STEP1,            \
      __VA_ARGS__ | LAST_STEP }

#define ANY_FLAG_PERMUTATION(...)    \
    { __VA_ARGS__,  /* ZF=0, CF=0 */ \
      __VA_ARGS__,  /* ZF=0, CF=1 */ \
      __VA_ARGS__,  /* ZF=1, CF=0 */ \
      __VA_ARGS__ } /* ZF=1, CF=1 */

#define CARRY_FLAG_PERMUTATION(...)      \
    { ONLY_FETCH_STEPS, /* ZF=0, CF=0 */ \
      __VA_ARGS__,      /* ZF=0, CF=1 */ \
      ONLY_FETCH_STEPS, /* ZF=1, CF=0 */ \
      __VA_ARGS__ }     /* ZF=1, CF=1 */

#define ZERO_FLAG_PERMUTATION(...)       \
    { ONLY_FETCH_STEPS, /* ZF=0, CF=0 */ \
      ONLY_FETCH_STEPS, /* ZF=0, CF=1 */ \
      __VA_ARGS__,      /* ZF=1, CF=0 */ \
      __VA_ARGS__ }     /* ZF=1, CF=1 */

static const uint16_t instructionSteps[NB_OF_INSTRUCTIONS][NB_OF_FLAG_PERMUTATIONS][MAX_NB_OF_CONTROL_STEPS] = {
    // 0x0: nop
    ANY_FLAG_PERMUTATION(
        ONLY_FETCH_STEPS),

    // 0x1: a = mem[immediate]
    ANY_FLAG_PERMUTATION(
        WITH_FETCH_STEPS(
            IR_BUS_OUT | MAR_BUS_IN,
            RAM_BUS_OUT | REGA_BUS_IN)),

    // 0x2: a += mem[immediate]
    ANY_FLAG_PERMUTATION(
        WITH_FETCH_STEPS(
            IR_BUS_OUT | MAR_BUS_IN,
            RAM_BUS_OUT | REGB_BUS_IN,
            ALU_BUS_OUT | FLAGS_IN | REGA_BUS_IN)),

    // 0x3: a -= mem[immediate]
    ANY_FLAG_PERMUTATION(
        WITH_FETCH_STEPS(
            IR_BUS_OUT | MAR_BUS_IN,
            RAM_BUS_OUT | REGB_BUS_IN | ALU_SUBTRACT,
            ALU_SUBTRACT | ALU_BUS_OUT | FLAGS_IN | REGA_BUS_IN)),

    // 0x4: mem[immediate] = a
    ANY_FLAG_PERMUTATION(
        WITH_FETCH_STEPS(
            IR_BUS_OUT | MAR_BUS_IN,
            REGA_BUS_OUT | RAM_BUS_IN)),

    // 0x5: a = immediate
    ANY_FLAG_PERMUTATION(
        WITH_FETCH_STEPS(
            IR_BUS_OUT | REGA_BUS_IN)),

    // 0x6: goto immediate
    ANY_FLAG_PERMUTATION(
        WITH_FETCH_STEPS(
            IR_BUS_OUT | PC_BUS_IN)),

    // 0x7: goto immediate when carry
    CARRY_FLAG_PERMUTATION(
        WITH_FETCH_STEPS(
            IR_BUS_OUT | PC_BUS_IN)),

    // 0x8: goto immediate when zero
    ZERO_FLAG_PERMUTATION(
        WITH_FETCH_STEPS(
            IR_BUS_OUT | PC_BUS_IN)),

    // 0x9:
    ANY_FLAG_PERMUTATION(
        ONLY_FETCH_STEPS),

    // 0xa:
    ANY_FLAG_PERMUTATION(
        ONLY_FETCH_STEPS),

    // 0xb:
    ANY_FLAG_PERMUTATION(
        ONLY_FETCH_STEPS),

    // 0xc:
    ANY_FLAG_PERMUTATION(
        ONLY_FETCH_STEPS),

    // 0xd:
    ANY_FLAG_PERMUTATION(
        ONLY_FETCH_STEPS),

    // 0xe: out = a
    ANY_FLAG_PERMUTATION(
        WITH_FETCH_STEPS(
            REGA_BUS_OUT | OUT_BUS_IN)),

    // 0xf: halt
    ANY_FLAG_PERMUTATION(
        WITH_FETCH_STEPS(
            HALT)),
};

const uint16_t resetSteps[MAX_NB_OF_CONTROL_STEPS] =
    {ZERO_BUS_OUT | PC_BUS_IN,
     REGA_BUS_IN,
     REGB_BUS_IN,
     OUT_BUS_IN,
     MAR_BUS_IN,
     RAM_BUS_OUT | IR_BUS_IN | LAST_STEP};

enum OUTPUT_FORMAT {
    BINARY_OUTPUT,
    HEX_STRING_OUTPUT
};

int writeToFile(const enum OUTPUT_FORMAT format, const char *filename, const uint8_t (*data)[32768]) {
    FILE *file = fopen(filename, "w");

    enum { SIZE = sizeof(*data) };

    if (file == NULL) {
        return errno;
    } else {
        if (format == BINARY_OUTPUT) {
            fwrite(data, sizeof(uint8_t), SIZE, file);
        } else if (format == HEX_STRING_OUTPUT) {
            enum { HEX_STRING_LEN = 3 }; // "FF\n" is 3 chars for each uint8_t.

            char hex[SIZE * HEX_STRING_LEN];

            for (int i = 0; i < SIZE; ++i) {
                snprintf(hex + (i * HEX_STRING_LEN), HEX_STRING_LEN, "%02x", (*data)[i]);

                // Replace '\0' introduced by snprintf with '\n'.
                hex[(i * HEX_STRING_LEN) + (HEX_STRING_LEN - 1)] = '\n';
            }

            fputs("v2.0 raw\n", file);
            fwrite(hex, sizeof(char), sizeof(hex), file);
        }

        fclose(file);

        return ferror(file);
    }
}

void makeMicrocode(uint8_t (*microcode)[32768]) {
    enum { SIZE = sizeof(*microcode) };

    for (uint16_t i = 0; i < SIZE; ++i) {
        const uint8_t step = i & 0x7;          // 3 bits.
        const uint8_t opcode = (i >> 3) & 0xf; // 4 bits.
        const uint8_t flag = (i >> 7) & 0x3;   // 2 bits.
        const uint8_t reset = (i >> 13) & 1;
        const uint8_t highByte = (i >> 14) & 1;

        const uint16_t controlWord =
            reset == 1
                ? resetSteps[step]
                : instructionSteps[opcode][flag][step];

        (*microcode)[i] =
            highByte == 1
                ? controlWord >> 8
                : controlWord & 0xff;
    }
}

void printUsage() {
    printf("Usage: Microcode [h] -O output filename\n");
}

int main(int argc, char *argv[]) {
    if (argc < 1) {
        printUsage();
        return 1;
    }

    // Start with `:` to disable getopt error printing.
    // -h Help. (h).
    // -O Output file of low microcode. Required (O:).
    const char *cliOptions = ":hO:";
    int currentOption = -1;

    char *microcodeFilename = "";

    while ((currentOption = getopt(argc, argv, cliOptions)) != -1) {
        switch (currentOption) {

        case 'O':
            microcodeFilename = optarg;
            break;

        case 'h':
            printUsage();
            return 0;

        case ':':
            printf("Missing arg for '%c'\n", optopt);
            printUsage();
            return 1;

        case '?':
            printf("Unknown option '%c'\n", optopt);
            printUsage();
            return 1;
        }
    }

    uint8_t microcode[32768] = {0};

    makeMicrocode(&microcode);

    const int result = writeToFile(HEX_STRING_OUTPUT, microcodeFilename, &microcode);

    if (result != 0) {
        fprintf(stderr, "Cannot write file '%s': %s.\n",
                microcodeFilename, strerror(result));

        return 1;
    } else {
        printf("Done!\n");
        return 0;
    }
}
