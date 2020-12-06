const fs = require("fs");

const RAM = 1;
const PC = 2;
const IR = 3;
const REGA = 4;
const REGB = 5;
const ALU = 6;
const MAR = 7;
const OUT = 8;

const HALT = 1 << 6;
const PC_COUNT = 1 << 7;
const ALU_SUBTRACT = 1 << 8;
const OUT_SIGNED = 1 << 9;
const INSTRUCTION_COMPLETE = 1 << 15;

const busOut = (from) => {
  switch (from) {
    case RAM:
      return 1;
    case PC:
      return 2;
    case IR:
      return 3;
    case REGA:
      return 4;
    case REGB:
      return 5;
    case ALU:
      return 6;
    default:
      throw new Error("busOut: Invalid from " + from);
  }
};

const busIn = (to) => {
  switch (to) {
    case RAM:
      return 1 << 3;
    case MAR:
      return 2 << 3;
    case PC:
      return 3 << 3;
    case IR:
      return 4 << 3;
    case REGA:
      return 5 << 3;
    case REGB:
      return 6 << 3;
    case OUT:
      return 7 << 3;
    default:
      throw new Error("busIn: Invalid to " + from);
  }
};

const instructions = [
  [
    // nop
    busOut(PC) | busIn(MAR),
    busOut(RAM) | busIn(IR) | PC_COUNT | INSTRUCTION_COMPLETE,
  ],
  [
    // lda
    busOut(PC) | busIn(MAR),
    busOut(RAM) | busIn(IR) | PC_COUNT,
    busOut(IR) | busIn(MAR),
    busOut(RAM) | busIn(REGA) | INSTRUCTION_COMPLETE,
  ],
];

const generate = ({ instructionBits, stepBits }) => {
  const high = Array.from({ length: 2 << 7 }, () => 0); // 1 entry should be 8 bits.
  const low = Array.from({ length: 2 << 7 }, () => 0); // 1 entry should be 8 bits.

  for (
    let instructionIndex = 0;
    instructionIndex < 1 << instructionBits;
    ++instructionIndex
  ) {
    for (let step = 0; step < 1 << stepBits; ++step) {
      const microCode = (instructions[instructionIndex] || [])[step] || HALT;

      high[(instructionIndex << stepBits) + step] = (microCode >> 8) & 0xff;
      low[(instructionIndex << stepBits) + step] = microCode & 0xff;
    }
  }

  return { high, low };
};

const args = process.argv.slice(2);
const highRomHexFile = args[0];
const lowRomHexFile = args[1];

if (args.length !== 2) {
  console.error(
    "Missing rom output filenames, MicroCodeHigh.hex MicroCodeLow.hex"
  );
  process.exit(1);
}

const roms = generate({
  instructionBits: 4,
  stepBits: 4,
});

const toHex = (v) => v.toString(16).padStart(2, "0");

fs.writeFileSync(
  highRomHexFile,
  "v2.0 raw\n" + roms.high.map(toHex).join("\n"),
  "utf-8"
);

fs.writeFileSync(
  lowRomHexFile,
  "v2.0 raw\n" + roms.low.map(toHex).join("\n"),
  "utf-8"
);
