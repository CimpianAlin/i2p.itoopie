package net.i2p.router.tunnel;

import net.i2p.I2PAppContext;
import net.i2p.util.DecayingBloomFilter;

/**
 * Manage the IV validation for all of the router's tunnels by way of a big
 * decaying bloom filter.  
 *
 */
public class BloomFilterIVValidator implements IVValidator {
    private I2PAppContext _context;
    private DecayingBloomFilter _filter;
    
    /**
     * After 2*halflife, an entry is completely forgotten from the bloom filter.
     * To avoid the issue of overlap within different tunnels, this is set 
     * higher than it needs to be.
     *
     */
    private static final int HALFLIFE_MS = 10*60*1000;
    public BloomFilterIVValidator(I2PAppContext ctx, int KBps) {
        _context = ctx;
        _filter = new DecayingBloomFilter(ctx, HALFLIFE_MS, 16);
        ctx.statManager().createRateStat("tunnel.duplicateIV", "Note that a duplicate IV was received", "Tunnels", 
                                         new long[] { 10*60*1000l, 60*60*1000l, 3*60*60*1000l, 24*60*60*1000l });

    }
    
    public boolean receiveIV(byte[] iv) { 
        boolean dup = _filter.add(iv); 
        if (dup) _context.statManager().addRateData("tunnel.duplicateIV", 1, 1);
        return !dup; // return true if it is OK, false if it isn't
    }
    public void destroy() { _filter.stopDecaying(); }
}