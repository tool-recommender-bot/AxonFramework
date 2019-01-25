/*
 * Copyright (c) 2010-2019. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import com.fasterxml.jackson.annotation.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * Special Wrapped Token implementation that keeps track of two separate tokens, of which the streams have been merged
 * into a single one. This token keeps track of the progress of the two original "halves", by advancing each
 * individually, until both halves represent the same position.
 */
public class MergedTrackingToken implements TrackingToken, Serializable, WrappedToken {

    private static final long serialVersionUID = 382974732408053911L;

    private final TrackingToken lowerSegmentToken;
    private final TrackingToken upperSegmentToken;

    private final transient boolean lowerSegmentAdvanced;
    private final transient boolean upperSegmentAdvanced;

    /**
     * Initialize a Merged Token, with the {@code lowerSegmentToken} representing the progress of the segment with the
     * lower segmentId, and {@code upperSegmentToken} representing the progress of the segment with the higher segmentId.
     *
     * @param lowerSegmentToken the token of the half with the lower segment ID
     * @param upperSegmentToken the token of the half with the higher segment ID
     */
    @JsonCreator
    public MergedTrackingToken(
            @JsonProperty("lowerSegmentToken") TrackingToken lowerSegmentToken,
            @JsonProperty("upperSegmentToken") TrackingToken upperSegmentToken) {
        this(lowerSegmentToken, upperSegmentToken, false, false);
    }

    /**
     * Initialize a Merged Token, with the {@code lowerSegmentToken} representing the progress of the segment with the
     * lower segmentId, and {@code upperSegmentToken} representing the progress of the segment with the higher segmentId,
     * additionally indicating if either of these segments were advanced by the latest call to
     * {@link #advancedTo(TrackingToken)}
     *
     * @param lowerSegmentToken    the token of the half with the lower segment ID
     * @param upperSegmentToken    the token of the half with the higher segment ID
     * @param lowerSegmentAdvanced whether the lower segment advanced in the last call
     * @param upperSegmentAdvanced whether the upper segment advanced in the last call
     */
    protected MergedTrackingToken(TrackingToken lowerSegmentToken, TrackingToken upperSegmentToken,
                                  boolean lowerSegmentAdvanced, boolean upperSegmentAdvanced) {
        this.lowerSegmentToken = lowerSegmentToken;
        this.upperSegmentToken = upperSegmentToken;
        this.lowerSegmentAdvanced = lowerSegmentAdvanced;
        this.upperSegmentAdvanced = upperSegmentAdvanced;
    }

    @Override
    public TrackingToken lowerBound(TrackingToken other) {
        TrackingToken newLowerSegmentToken = lowerSegmentToken == null ? null : lowerSegmentToken.lowerBound(other);
        TrackingToken newUpperSegmentToken = upperSegmentToken == null ? null : upperSegmentToken.lowerBound(other);
        if (Objects.equals(newLowerSegmentToken, newUpperSegmentToken)) {
            return newLowerSegmentToken;
        }
        return new MergedTrackingToken(newLowerSegmentToken, newUpperSegmentToken);
    }

    @Override
    public TrackingToken upperBound(TrackingToken other) {
        TrackingToken newLowerSegmentToken = doAdvance(lowerSegmentToken, other);
        TrackingToken newUpperSegmentToken = doAdvance(upperSegmentToken, other);
        if (Objects.equals(newLowerSegmentToken, newUpperSegmentToken)) {
            return newLowerSegmentToken;
        }
        return new MergedTrackingToken(newLowerSegmentToken, newUpperSegmentToken);
    }

    @Override
    public boolean covers(TrackingToken other) {
        return lowerSegmentToken.covers(other)
                && upperSegmentToken.covers(other);
    }

    @Override
    public TrackingToken advancedTo(TrackingToken newToken) {
        TrackingToken newLowerSegmentToken = doAdvance(lowerSegmentToken, newToken);
        TrackingToken newUpperSegmentToken = doAdvance(upperSegmentToken, newToken);
        boolean lowerSegmentAdvanced = !Objects.equals(newLowerSegmentToken, lowerSegmentToken);
        boolean upperSegmentAdvanced = !Objects.equals(newUpperSegmentToken, upperSegmentToken);
        if (lowerSegmentAdvanced && upperSegmentAdvanced && Objects.equals(newLowerSegmentToken, newUpperSegmentToken)) {
            return newLowerSegmentToken;
        }
        return new MergedTrackingToken(newLowerSegmentToken, newUpperSegmentToken,
                                       lowerSegmentAdvanced, upperSegmentAdvanced);
    }

    @Override
    public <R extends TrackingToken> Optional<R> unwrap(Class<R> tokenType) {
        if (tokenType.isInstance(this)) {
            return Optional.of(tokenType.cast(this));
        } else if (lowerSegmentAdvanced) {
            return WrappedToken.unwrap(lowerSegmentToken, tokenType);
        } else if (upperSegmentAdvanced) {
            return WrappedToken.unwrap(upperSegmentToken, tokenType);
        } else {
            // let's see if either works
            return Optional.empty();
        }
    }

    private TrackingToken doAdvance(TrackingToken currentToken, TrackingToken newToken) {
        if (currentToken == null) {
            return newToken;
        } else if (currentToken instanceof WrappedToken) {
            if (currentToken.covers(newToken)) {
                // this segment is still way ahead.
                return currentToken;
            } else {
                return ((WrappedToken) currentToken).advancedTo(newToken);
            }
        }
        return currentToken.upperBound(newToken);
    }

    @Override
    public TrackingToken lowerBound() {
        TrackingToken lower = WrappedToken.unwrapLowerBound(lowerSegmentToken);
        TrackingToken upper = WrappedToken.unwrapLowerBound(upperSegmentToken);

        return lower == null || upper == null ? null : lower.lowerBound(upper);
    }

    @Override
    public TrackingToken upperBound() {
        TrackingToken lower = WrappedToken.unwrapUpperBound(lowerSegmentToken);
        TrackingToken upper = WrappedToken.unwrapUpperBound(upperSegmentToken);

        return lower == null || upper == null ? null : lower.upperBound(upper);
    }

    /**
     * Returns the token indicating the progress of the lower half (the half with the lower segmentId) of the merged
     * segment represented by this token
     *
     * @return the token indicating the progress of the lower half of the merged segment
     */
    @JsonGetter("lowerSegmentToken")
    @JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
    public TrackingToken lowerSegmentToken() {
        return lowerSegmentToken;
    }

    /**
     * Returns the token indicating the progress of the upper half (the half with the higher segmentId) of the merged
     * segment represented by this token
     *
     * @return the token indicating the progress of the upper half of the merged segment
     */
    @JsonGetter("upperSegmentToken")
    @JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
    public TrackingToken upperSegmentToken() {
        return upperSegmentToken;
    }

    /**
     * Indicates whether the last call to {@link #advancedTo(TrackingToken)} caused the lower segment to advance
     *
     * @return true if the last advancement moved the lower segment
     */
    @JsonIgnore
    public boolean isLowerSegmentAdvanced() {
        return lowerSegmentAdvanced;
    }

    /**
     * Indicates whether the last call to {@link #advancedTo(TrackingToken)} caused the upper segment to advance
     *
     * @return true if the last advancement moved the upper segment
     */
    @JsonIgnore
    public boolean isUpperSegmentAdvanced() {
        return upperSegmentAdvanced;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MergedTrackingToken that = (MergedTrackingToken) o;
        return Objects.equals(lowerSegmentToken, that.lowerSegmentToken) &&
                Objects.equals(upperSegmentToken, that.upperSegmentToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lowerSegmentToken, upperSegmentToken);
    }

    @Override
    public String toString() {
        return "MergedTrackingToken{" +
                "lowerSegmentToken=" + lowerSegmentToken +
                ", upperSegmentToken=" + upperSegmentToken +
                '}';
    }
}