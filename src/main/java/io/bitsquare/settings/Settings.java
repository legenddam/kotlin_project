package io.bitsquare.settings;

import com.google.inject.Inject;
import io.bitsquare.user.Arbitrator;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Settings implements Serializable
{
    private static final long serialVersionUID = 7995048077355006861L;

    private List<Locale> acceptedLanguageLocales = new ArrayList<>();
    private List<Locale> acceptedCountryLocales = new ArrayList<>();
    private List<Arbitrator> acceptedArbitrators = new ArrayList<>();
    private double maxCollateral;
    private double minCollateral;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Settings()
    {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void updateFromStorage(Settings savedSettings)
    {
        if (savedSettings != null)
        {
            acceptedLanguageLocales = savedSettings.getAcceptedLanguageLocales();
            acceptedCountryLocales = savedSettings.getAcceptedCountryLocales();
            acceptedArbitrators = savedSettings.getAcceptedArbitrators();
            maxCollateral = savedSettings.getMaxCollateral();
            minCollateral = savedSettings.getMinCollateral();
        }
    }

    public void addAcceptedLanguageLocale(Locale locale)
    {
        if (!acceptedLanguageLocales.contains(locale))
            acceptedLanguageLocales.add(locale);
    }

    public void addAcceptedCountryLocale(Locale locale)
    {
        if (!acceptedCountryLocales.contains(locale))
            acceptedCountryLocales.add(locale);
    }

    public void addAcceptedArbitrator(Arbitrator arbitrator)
    {
        if (!acceptedArbitrators.contains(arbitrator))
            acceptedArbitrators.add(arbitrator);
    }

    public void setMaxCollateral(double maxCollateral)
    {
        this.maxCollateral = maxCollateral;
    }

    public void setMinCollateral(double minCollateral)
    {
        this.minCollateral = minCollateral;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<Arbitrator> getAcceptedArbitrators()
    {
        return acceptedArbitrators;
    }

    public List<Locale> getAcceptedLanguageLocales()
    {
        return acceptedLanguageLocales;
    }

    public List<Locale> getAcceptedCountryLocales()
    {
        return acceptedCountryLocales;
    }

    public Arbitrator getRandomArbitrator(double collateral, BigInteger amount)
    {
        List<Arbitrator> candidates = new ArrayList<>();
        for (Arbitrator arbitrator : acceptedArbitrators)
        {
            if (arbitrator.getArbitrationFeePercent() >= collateral &&
                    arbitrator.getMinArbitrationFee().compareTo(amount) < 0)
            {
                candidates.add(arbitrator);
            }
        }
        return candidates.size() > 0 ? candidates.get((int) (Math.random() * candidates.size())) : null;
    }

    public double getMaxCollateral()
    {
        return maxCollateral;
    }

    public double getMinCollateral()
    {
        return minCollateral;
    }

}
