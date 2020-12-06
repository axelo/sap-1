const fourBitToSeventSegment = {
  // Common cathod, 1 = On, 0 = Off.
  //     abcdefgdp
  0x0: 0b11111100,
  0x1: 0b01100000,
  0x2: 0b11011010,
  0x3: 0b11110010,
  0x4: 0b01100110,
  0x5: 0b10110110,
  0x6: 0b10111110,
  0x7: 0b11100000,
  0x8: 0b11111110,
  0x9: 0b11110110,
  0xa: 0b11101110,
  0xb: 0b00111110,
  0xc: 0b10011100,
  0xd: 0b01111010,
  0xe: 0b10011110,
  0xf: 0b10001110,
};

const toSeventSegment = (v) => fourBitToSeventSegment[v] || 0;
const toHex = (v) => v.toString(16).padStart(2, "0");

const rom = Array.from({ length: 2048 }, () => 0); // 1 entry should be 8 bits.

for (let i = 0; i < 0x100; ++i) {
  rom[i] = toSeventSegment(i % 10);
  rom[i + 0x100] = toSeventSegment(Math.floor(i / 10) % 10);
  rom[i + 0x200] = toSeventSegment(Math.floor(i / 100) % 10);
  rom[i + 0x300] = 0;
}

for (let i = -128; i < 128; ++i) {
  rom[(i & 0xff) + 0x400] = toSeventSegment(Math.abs(i) % 10);
  rom[(i & 0xff) + 0x500] = toSeventSegment(Math.floor(Math.abs(i) / 10) % 10);
  rom[(i & 0xff) + 0x600] = toSeventSegment(Math.floor(Math.abs(i) / 100) % 10);
  rom[(i & 0xff) + 0x700] = i < 0 ? 0b00000010 : 0;
}

console.log(rom.map(toHex).join("\n"));
