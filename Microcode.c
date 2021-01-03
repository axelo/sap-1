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
    // 1 << 10 Unused.
    // 1 << 11 Unused.
    // 1 << 12 Unused.
    // 1 << 13 Unused.
    // 1 << 14 Unused.
    LAST_STEP = 1 << 15 // Last step bit.
};

enum {
    MAX_NB_OF_CONTROL_STEPS = 16, // Low 4 bits of microcode address.
    NB_OF_INSTRUCTIONS = 16,      // High 4 bits of microcode address.
    DEFAULT_MICROCODE = HALT | LAST_STEP
};

const uint16_t fetchSteps[] =
    {PC_BUS_OUT | MAR_BUS_IN,
     RAM_BUS_OUT | IR_BUS_IN | PC_COUNT};

enum { FETCH_STEPS_LENGTH = sizeof(fetchSteps) / sizeof(fetchSteps[0]) };

const uint16_t instructionSteps[NB_OF_INSTRUCTIONS][MAX_NB_OF_CONTROL_STEPS] = {
    // 0x0: nop
    {LAST_STEP},

    // 0x1: a = mem[immediate]
    {
        IR_BUS_OUT | MAR_BUS_IN,
        RAM_BUS_OUT | REGA_BUS_IN | LAST_STEP,
    },

    // 0x2: a += mem[immediate]
    {
        IR_BUS_OUT | MAR_BUS_IN,
        RAM_BUS_OUT | REGB_BUS_IN,
        ALU_BUS_OUT | REGA_BUS_IN | LAST_STEP,
    },

    // 0x3: a -= mem[immediate]
    {
        IR_BUS_OUT | MAR_BUS_IN,
        RAM_BUS_OUT | REGB_BUS_IN,
        ALU_SUBTRACT | ALU_BUS_OUT | REGA_BUS_IN | LAST_STEP,
    },

    // 0x4: mem[immediate] = a
    {
        IR_BUS_OUT | MAR_BUS_IN,
        REGA_BUS_OUT | RAM_BUS_IN | LAST_STEP,
    },

    // 0x5: a = immediate
    {
        IR_BUS_OUT | REGA_BUS_IN | LAST_STEP,
    },

    // 0x6: goto immediate
    {
        IR_BUS_OUT | PC_BUS_IN | LAST_STEP,
    },

    // 0x7:
    {LAST_STEP},

    // 0x8:
    {LAST_STEP},

    // 0x9:
    {LAST_STEP},

    // 0xa:
    {LAST_STEP},

    // 0xb:
    {LAST_STEP},

    // 0xc:
    {LAST_STEP},

    // 0xd:
    {LAST_STEP},

    // 0xe: out = a
    {
        REGA_BUS_OUT | OUT_BUS_IN | LAST_STEP,
    },

    // 0xf: halt
    {HALT | LAST_STEP},
};

// Reset steps at 0x400 beacuse address bit 10 is held high until first encountered LAST_STEP.
// Assumes IR and STEP is zero at startup.
// TODO: Add reset stes at all possible IR values to remove 0 at start up requirement.
const uint16_t resetAddress = 1 << 10;

const uint16_t resetSteps[] =
    {
        ZERO_BUS_OUT | PC_BUS_IN,
        REGA_BUS_IN,
        REGB_BUS_IN,
        OUT_BUS_IN,
        MAR_BUS_IN,
        RAM_BUS_OUT | IR_BUS_IN | LAST_STEP};

enum OUTPUT_FORMAT {
    BINARY_OUTPUT,
    HEX_STRING_OUTPUT
};

int writeToFile(const enum OUTPUT_FORMAT format, const char *filename, const uint8_t (*rom)[2048]) {
    FILE *file = fopen(filename, "w");

    if (file == NULL) {
        return errno;
    } else {
        if (format == BINARY_OUTPUT) {
            fwrite(rom, sizeof(uint8_t), 2048, file);
        } else if (format == HEX_STRING_OUTPUT) {
            enum { HEX_STRING_LEN = 3 }; // "FF\n" is 3 chars for each uint8_t.

            char hex[2048 * HEX_STRING_LEN];

            for (int i = 0; i < 2048; ++i) {
                snprintf(hex + (i * HEX_STRING_LEN), HEX_STRING_LEN, "%02x", (*rom)[i]);

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

void makeMicrocodes(uint8_t (*microcodeLow)[2048], uint8_t (*microcodeHigh)[2048]) {
    for (int i = 0; i < NB_OF_INSTRUCTIONS; ++i) {
        for (int s = 0; s < FETCH_STEPS_LENGTH; ++s) {
            const uint16_t address = (i << 4) + s;
            const uint16_t code = fetchSteps[s];

            (*microcodeLow)[address] = code & 0xff;
            (*microcodeHigh)[address] = (code >> 8) & 0xff;
        }

        for (int s = 0; s < (MAX_NB_OF_CONTROL_STEPS - FETCH_STEPS_LENGTH); ++s) {
            const uint16_t address = (i << 4) + FETCH_STEPS_LENGTH + s;
            uint16_t code = instructionSteps[i][s];

            if (code == 0) {
                code = DEFAULT_MICROCODE;
            }

            (*microcodeLow)[address] = code & 0xff;
            (*microcodeHigh)[address] = (code >> 8) & 0xff;
        }
    }

    // Fill in possible instructions with fetch steps and DEFAULT_MICROCODE.
    for (int i = NB_OF_INSTRUCTIONS; i < 16; ++i) {
        for (int s = 0; s < FETCH_STEPS_LENGTH; ++s) {
            const uint16_t address = (i << 4) + s;
            const uint16_t code = fetchSteps[s];

            (*microcodeLow)[address] = code & 0xff;
            (*microcodeHigh)[address] = (code >> 8) & 0xff;
        }

        for (int s = FETCH_STEPS_LENGTH; s < MAX_NB_OF_CONTROL_STEPS; ++s) {
            const uint16_t address = (i << 4) + s; // To be able to fill 2k, 8 bits won't fit.
            const uint16_t code = DEFAULT_MICROCODE;

            (*microcodeLow)[address] = code & 0xff;
            (*microcodeHigh)[address] = (code >> 8) & 0xff;
        }
    }

    for (int s = 0; s < (int)(sizeof(resetSteps) / sizeof(resetSteps[0])); ++s) {
        const uint16_t address = resetAddress + s;
        const uint16_t code = resetSteps[s];

        (*microcodeLow)[address] = code & 0xff;
        (*microcodeHigh)[address] = (code >> 8) & 0xff;
    }
}

void printUsage() {
    printf("Usage: Microcode [h] -L low output filename -H high output filename\n");
}

int main(int argc, char *argv[]) {
    if (argc < 2) {
        printUsage();
        return 1;
    }

    // Start with `:` to disable getopt error printing.
    // -h Help. (h).
    // -L Output file of low microcode. Required (L:).
    // -H Output file of high microcode. Required (H:).
    const char *cliOptions = ":hL:H:";
    int currentOption = -1;

    char *microCodeLowFilename = "";
    char *microCodeHighFilename = "";

    while ((currentOption = getopt(argc, argv, cliOptions)) != -1) {
        switch (currentOption) {

        case 'L':
            microCodeLowFilename = optarg;
            break;

        case 'H':
            microCodeHighFilename = optarg;
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

    uint8_t microcodeLow[2048] = {0};
    uint8_t microcodeHigh[2048] = {0};

    makeMicrocodes(&microcodeLow, &microcodeHigh);

    const int resultLow = writeToFile(HEX_STRING_OUTPUT, microCodeLowFilename, &microcodeLow);
    const int resultHigh = writeToFile(HEX_STRING_OUTPUT, microCodeHighFilename, &microcodeHigh);

    if (resultLow | resultHigh) {
        if (resultLow != 0) {
            fprintf(stderr, "Cannot write file '%s': %s.\n",
                    microCodeLowFilename, strerror(resultLow));
        }

        if (resultHigh != 0) {
            fprintf(stderr, "Cannot write file '%s': %s.\n",
                    microCodeHighFilename, strerror(resultHigh));
        }

        return 1;
    } else {
        printf("Done!\n");
        return 0;
    }
}
