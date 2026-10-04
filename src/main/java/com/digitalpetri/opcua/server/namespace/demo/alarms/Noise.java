package com.digitalpetri.opcua.server.namespace.demo.alarms;

import java.util.Random;

/**
 * Gaussian measurement noise for a simulated instrument.
 *
 * <p>Seeded per model, so a model's trace is the same on every run of the server and an alarm that
 * trips at a given tick keeps tripping there.
 */
final class Noise {

  private final Random random;

  Noise(long seed) {
    this.random = new Random(seed);
  }

  /**
   * Sample the noise to add to an instrument reading.
   *
   * @param deviation the standard deviation of the sample, in the reading's engineering units.
   * @return the sampled noise.
   */
  double sample(double deviation) {
    return random.nextGaussian() * deviation;
  }
}
