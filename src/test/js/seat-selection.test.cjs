const test = require('node:test');
const assert = require('node:assert/strict');
const { createSeatMap, selectionStatus } = require('../../main/resources/static/js/seat-selection.js');

test('renders configured capacity, including seats beyond the old 20-seat limit', () => {
  const seats = createSeatMap('FIRST', 60, ['Car 01 / S23']);
  assert.equal(seats.length, 60);
  assert.equal(seats[22].occupied, true);
  assert.equal(seats[59].label, 'Car 01 / S60');
});

test('second class uses its own carriage and occupied identities', () => {
  const seats = createSeatMap('SECOND', 24, ['Car 02 / S03', 'Car 01 / S04']);
  assert.equal(seats[2].occupied, true);
  assert.equal(seats[3].occupied, false);
  assert.equal(seats[23].label, 'Car 02 / S24');
});

test('invalid or missing capacity creates no selectable seats', () => {
  for (const capacity of [0, -1, undefined, 1.5, '20']) {
    assert.deepEqual(createSeatMap('FIRST', capacity, []), []);
  }
});

test('nonadjacent available selected seats can be submitted unchanged', () => {
  const seats = createSeatMap('FIRST', 10, ['Car 01 / S04']);
  const selection = ['Car 01 / S03', 'Car 01 / S07'];
  assert.equal(selectionStatus(seats, selection, 2, 9, null).canSubmit, true);
  assert.deepEqual(selection, ['Car 01 / S03', 'Car 01 / S07']);
});

test('occupied, wrong-class and out-of-range selections cannot be submitted', () => {
  const seats = createSeatMap('FIRST', 10, ['Car 01 / S04']);
  for (const label of ['Car 01 / S04', 'Car 02 / S03', 'Car 01 / S11']) {
    assert.equal(selectionStatus(seats, [label], 1, 9, null).canSubmit, false);
  }
});

test('missing, duplicated and mismatched quantities are rejected', () => {
  const seats = createSeatMap('FIRST', 10, []);
  assert.equal(selectionStatus(seats, [], 1, 10, null).canSubmit, false);
  assert.equal(selectionStatus(seats, ['Car 01 / S01'], 2, 10, null).canSubmit, false);
  assert.equal(selectionStatus(seats, ['Car 01 / S01', 'Car 01 / S01'], 2, 10, null).canSubmit, false);
});

test('server availability and six-seat limit remain authoritative', () => {
  const seats = createSeatMap('FIRST', 10, []);
  assert.equal(selectionStatus(seats, [seats[0].label], 1, 0, null).canSubmit, false);
  assert.equal(selectionStatus(seats, seats.slice(0, 7).map(s => s.label), 7, 10, null).canSubmit, false);
});

test('legacy allocation warning blocks new selections', () => {
  const seats = createSeatMap('FIRST', 10, []);
  assert.equal(selectionStatus(seats, ['Car 01 / S01'], 1, 10, 'Staff review needed').canSubmit, false);
});
