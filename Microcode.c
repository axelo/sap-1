#include <errno.h>
#include <getopt.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

// 16 available control signals.
enum Signal {
    // 1 << 0 BUS OUT Address[0]
    // 1 << 1 BUS OUT Address[1]
    // 1 << 2 BUS OUT Address[2]
    // 1 << 3 BUS IN Address[0]
    // 1 << 4 BUS IN Address[1]
    // 1 << 5 BUS IN Address[2]
    HALT = 1 << 6,
    PC_COUNT = 1 << 7,
    ALU_SUBTRACT = 1 << 8,
    OUT_SIGNED = 1 << 9,
    // 1 << 10 Unused.
    // 1 << 11 Unused.
    // 1 << 12 Unused.
    // 1 << 13 Unused.
    // 1 << 14 Unused.
    LAST_STEP = 1 << 15 // Last step bit.
};

enum BusOutAddress {
    ZERO_OUT = 0,
    RAM_OUT = 1,
    PC_OUT = 2,
    IR_OUT = 3,
    REGA_OUT = 4,
    REGB_OUT = 5,
    ALU_OUT = 6
};

enum BusInAddress {
    RAM_IN = 1 << 3,
    MAR_IN = 2 << 3,
    PC_IN = 3 << 3,
    IR_IN = 4 << 3,
    REGA_IN = 5 << 3,
    REGB_IN = 6 << 3,
    OUT_IN = 7 << 3
};

enum {
    MAX_NB_OF_CONTROL_STEPS = 16, // Low 4 bits of microcode address.
    NB_OF_INSTRUCTIONS = 2,       // High 4 bits of microcode address.
    DEFAULT_MICROCODE = HALT | LAST_STEP
};

uint16_t microcode[NB_OF_INSTRUCTIONS][MAX_NB_OF_CONTROL_STEPS] = {
    // nop
    {LAST_STEP},
    // mov a, [immediate]
    {
        IR_OUT | MAR_IN,
        RAM_OUT | REGA_IN | LAST_STEP,
    },
};

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
            enum { HEX_STRING_LEN = 5 }; // "0xFF\n" is 5 chars for each uint8_t.

            char hex[2048 * HEX_STRING_LEN];

            for (int i = 0; i < 2048; ++i) {
                snprintf(hex + (i * HEX_STRING_LEN), HEX_STRING_LEN, "0x%02x", (*rom)[i]);

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
    const int fetchSteps[] =
        {PC_OUT | MAR_IN,
         RAM_OUT | IR_IN | PC_COUNT};

    enum { FETCH_STEPS_LENGTH = sizeof(fetchSteps) / sizeof(fetchSteps[0]) };

    for (int i = 0; i < NB_OF_INSTRUCTIONS; ++i) {
        for (int s = 0; s < FETCH_STEPS_LENGTH; ++s) {
            const uint16_t address = (i << 4) + s;
            const uint16_t code = fetchSteps[s];

            (*microcodeLow)[address] = code & 0xff;
            (*microcodeHigh)[address] = (code >> 8) & 0xff;
        }

        for (int s = 0; s < (MAX_NB_OF_CONTROL_STEPS - FETCH_STEPS_LENGTH); ++s) {
            const uint16_t address = (i << 4) + FETCH_STEPS_LENGTH + s;
            uint16_t code = microcode[i][s];

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

    // Reset steps at 0x400 beacuse address bit 10 is held high until first encountered LAST_STEP.
    // Assumes IR and STEP is zero at startup.
    const uint16_t resetAddress = 1 << 10;

    const int resetSteps[] =
        {
            ZERO_OUT | PC_IN,
            REGA_IN,
            REGB_IN,
            OUT_IN,
            MAR_IN,
            RAM_OUT | IR_IN | LAST_STEP};

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
