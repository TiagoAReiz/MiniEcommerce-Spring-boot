package reiz.miniecommerce.testsupport;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates CPFs that survive real validation.
 *
 * <p>Needed because {@code uq_users_cpf} is global and the database outlives a run, so tests
 * cannot share a literal; and because the check digits are enforced now, so they cannot use
 * arbitrary digits either.
 */
public final class Cpfs {

    private Cpfs() {
    }

    /** A valid, structurally distinct CPF. Repeated-digit sequences are never produced. */
    public static String random() {
        int[] digits = new int[11];
        do {
            for (int i = 0; i < 9; i++) {
                digits[i] = ThreadLocalRandom.current().nextInt(10);
            }
        } while (allTheSame(digits));

        digits[9] = checkDigit(digits, 9, 10);
        digits[10] = checkDigit(digits, 10, 11);

        StringBuilder cpf = new StringBuilder(11);
        for (int digit : digits) {
            cpf.append(digit);
        }
        return cpf.toString();
    }

    /**
     * Weighted sum of the digits so far, counting down from {@code startingWeight}. A
     * remainder below two means the digit is zero — the rule that makes the last two digits
     * derivable from the first nine.
     */
    private static int checkDigit(int[] digits, int upTo, int startingWeight) {
        int sum = 0;
        for (int i = 0; i < upTo; i++) {
            sum += digits[i] * (startingWeight - i);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static boolean allTheSame(int[] digits) {
        for (int i = 1; i < 9; i++) {
            if (digits[i] != digits[0]) {
                return false;
            }
        }
        return true;
    }
}
